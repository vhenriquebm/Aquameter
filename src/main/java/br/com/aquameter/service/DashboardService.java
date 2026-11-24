package br.com.aquameter.service;

import br.com.aquameter.domain.Medidor;
import br.com.aquameter.domain.Propriedade;
import br.com.aquameter.domain.TipoMedidor;
import br.com.aquameter.repository.AlertaRepository;
import br.com.aquameter.repository.InsumoRepository;
import br.com.aquameter.repository.LeituraRepository;
import br.com.aquameter.repository.MedidorRepository;
import br.com.aquameter.repository.PropriedadeRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static br.com.aquameter.service.Formato.arred;

@Service
public class DashboardService {

    private final PropriedadeRepository propriedades;
    private final MedidorRepository medidores;
    private final LeituraRepository leituras;
    private final AlertaRepository alertas;
    private final InsumoRepository insumos;
    private final RecomendacaoService recomendacaoService;

    public DashboardService(PropriedadeRepository propriedades, MedidorRepository medidores,
                            LeituraRepository leituras, AlertaRepository alertas, InsumoRepository insumos,
                            RecomendacaoService recomendacaoService) {
        this.propriedades = propriedades;
        this.medidores = medidores;
        this.leituras = leituras;
        this.alertas = alertas;
        this.insumos = insumos;
        this.recomendacaoService = recomendacaoService;
    }

    public Dashboard montar(long propriedadeId) {
        Propriedade p = propriedades.buscar(propriedadeId)
                .orElseThrow(() -> new NaoEncontradoException("Propriedade " + propriedadeId + " não encontrada"));

        LocalDateTime agora = LocalDateTime.now();
        LocalDate hoje = agora.toLocalDate();
        LocalDateTime inicioHoje = hoje.atStartOfDay();
        LocalDateTime amanha = inicioHoje.plusDays(1);
        LocalDateTime ha7 = agora.minusDays(7);
        LocalDateTime ha14 = agora.minusDays(14);
        LocalDateTime ha30 = agora.minusDays(30);

        double aguaHoje = leituras.somaPorTipo(p.id(), TipoMedidor.AGUA, inicioHoje, amanha);
        double energiaHoje = leituras.somaPorTipo(p.id(), TipoMedidor.ENERGIA, inicioHoje, amanha);
        double aguaSemana = leituras.somaPorTipo(p.id(), TipoMedidor.AGUA, ha7, agora);
        double aguaAnterior = leituras.somaPorTipo(p.id(), TipoMedidor.AGUA, ha14, ha7);
        double energiaSemana = leituras.somaPorTipo(p.id(), TipoMedidor.ENERGIA, ha7, agora);
        double energiaAnterior = leituras.somaPorTipo(p.id(), TipoMedidor.ENERGIA, ha14, ha7);

        double custoAgua = leituras.somaPorTipo(p.id(), TipoMedidor.AGUA, ha30, agora) * p.tarifaAguaM3();
        double custoEnergia = leituras.somaPorTipo(p.id(), TipoMedidor.ENERGIA, ha30, agora) * p.tarifaEnergiaKwh();
        double custoInsumos = insumos.custoTotal(p.id(), hoje.minusDays(29), hoje.plusDays(1));

        List<Recomendacao> recomendacoes = recomendacaoService.gerar(p, agora);
        double economia = recomendacoes.stream()
                .map(Recomendacao::economiaMensalEstimada)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();

        Dashboard.Resumo resumo = new Dashboard.Resumo(
                arred(aguaHoje, 1), arred(energiaHoje, 1),
                arred(aguaSemana, 1), arred(aguaAnterior, 1), variacao(aguaSemana, aguaAnterior),
                arred(energiaSemana, 1), arred(energiaAnterior, 1), variacao(energiaSemana, energiaAnterior),
                arred(custoAgua, 2), arred(custoEnergia, 2), arred(custoInsumos, 2),
                arred(custoAgua + custoEnergia + custoInsumos, 2),
                alertas.contarAbertos(p.id()),
                arred(economia, 2));

        Map<Long, Double> consumoHoje = leituras.consumoPorMedidor(p.id(), inicioHoje, amanha);
        List<Dashboard.MedidorHoje> medidoresHoje = medidores.listarPorPropriedade(p.id()).stream()
                .map(m -> medidorHoje(m, consumoHoje.getOrDefault(m.id(), 0.0)))
                .toList();

        return new Dashboard(
                p,
                agora,
                resumo,
                leituras.serieDiaria(p.id(), hoje.minusDays(29)),
                medidoresHoje,
                alertas.listarPorPropriedade(p.id(), true),
                recomendacoes,
                insumos.custoPorCategoria(p.id(), hoje.minusDays(29), hoje.plusDays(1)));
    }

    private static Dashboard.MedidorHoje medidorHoje(Medidor m, double consumo) {
        return new Dashboard.MedidorHoje(m.id(), m.nome(), m.tipo().name(), m.setor(), m.unidade(),
                m.limiteDiario(), arred(consumo, 1), arred(consumo / m.limiteDiario() * 100, 0));
    }

    static double variacao(double atual, double anterior) {
        if (anterior <= 0) {
            return 0;
        }
        return arred((atual / anterior - 1) * 100, 1);
    }
}
