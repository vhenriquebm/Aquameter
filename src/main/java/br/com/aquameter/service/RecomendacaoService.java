package br.com.aquameter.service;

import br.com.aquameter.domain.Medidor;
import br.com.aquameter.domain.Propriedade;
import br.com.aquameter.domain.TipoMedidor;
import br.com.aquameter.repository.InsumoRepository;
import br.com.aquameter.repository.LeituraRepository;
import br.com.aquameter.repository.MedidorRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static br.com.aquameter.service.Formato.arred;
import static br.com.aquameter.service.Formato.num;
import static br.com.aquameter.service.Formato.reais;

/**
 * Motor de recomendações baseado em regras. Transforma os dados de consumo em ações concretas,
 * com estimativa de economia em reais — o principal valor percebido pelo produtor.
 */
@Service
public class RecomendacaoService {

    /** Energia gasta para bombear 1 m³ do poço (kWh/m³), usada para estimar o custo de um vazamento. */
    static final double KWH_POR_M3_BOMBEADO = 0.55;

    private final LeituraRepository leituras;
    private final MedidorRepository medidores;
    private final InsumoRepository insumos;
    private final double fatorPonta;

    public RecomendacaoService(LeituraRepository leituras, MedidorRepository medidores, InsumoRepository insumos,
                               @Value("${aquameter.energia.fator-ponta:2.0}") double fatorPonta) {
        this.leituras = leituras;
        this.medidores = medidores;
        this.insumos = insumos;
        this.fatorPonta = fatorPonta;
    }

    public List<Recomendacao> gerar(Propriedade p, LocalDateTime agora) {
        List<Recomendacao> lista = new ArrayList<>();
        LocalDateTime ha3dias = agora.minusDays(3);
        LocalDateTime ha7dias = agora.minusDays(7);
        LocalDateTime ha14dias = agora.minusDays(14);
        LocalDateTime ha17dias = agora.minusDays(17);

        // 1. Possível vazamento: consumo de madrugada acima do normal nos últimos 3 dias
        for (Medidor m : medidores.listarPorPropriedade(p.id())) {
            if (m.tipo() != TipoMedidor.AGUA) {
                continue;
            }
            double recente = leituras.mediaHorariaMadrugada(m.id(), ha3dias, agora);
            if (recente > m.limiteDiario() * AlertaService.FRACAO_MADRUGADA_VAZAMENTO) {
                double base = leituras.mediaHorariaMadrugada(m.id(), ha17dias, ha3dias);
                double perdaM3Mes = Math.max(0, recente - base) * 5 * 30;
                double custo = perdaM3Mes * (p.tarifaAguaM3() + KWH_POR_M3_BOMBEADO * p.tarifaEnergiaKwh());
                lista.add(new Recomendacao("ALTA",
                        "Possível vazamento: inspecione " + (m.setor() == null ? m.nome() : m.setor()),
                        "Entre 0h e 5h o consumo médio está em %s m³/h, contra %s m³/h nas semanas anteriores. Se for vazamento, a perda é de cerca de %s m³ por mês."
                                .formatted(num(recente, 2), num(base, 2), num(perdaM3Mes, 0)),
                        arred(custo, 2)));
            }
        }

        // 2. Energia no horário de ponta (18h às 21h), quando a tarifa rural é mais cara
        double energiaSemana = leituras.somaPorTipo(p.id(), TipoMedidor.ENERGIA, ha7dias, agora);
        double energiaPonta = leituras.somaEnergiaPonta(p.id(), ha7dias, agora);
        if (energiaSemana > 0 && energiaPonta / energiaSemana > 0.12) {
            double kwhPontaMes = energiaPonta / 7 * 30;
            double economia = kwhPontaMes * (fatorPonta - 1) * p.tarifaEnergiaKwh();
            lista.add(new Recomendacao("MÉDIA",
                    "Tire a irrigação do horário de ponta",
                    "%s%% da energia da última semana foi usada entre 18h e 21h. Antecipar o turno da tarde para antes das 17h reduz a conta em cerca de %s por mês."
                            .formatted(num(energiaPonta / energiaSemana * 100, 0), reais(economia)),
                    arred(economia, 2)));
        }

        // 3. Tendência semanal do consumo de água
        double aguaSemana = leituras.somaPorTipo(p.id(), TipoMedidor.AGUA, ha7dias, agora);
        double aguaAnterior = leituras.somaPorTipo(p.id(), TipoMedidor.AGUA, ha14dias, ha7dias);
        if (aguaAnterior > 0 && aguaSemana / aguaAnterior > 1.10) {
            lista.add(new Recomendacao("MÉDIA",
                    "Consumo de água em alta",
                    "Foram %s m³ nos últimos 7 dias, %s%% acima da semana anterior. Confira se houve mudança no turno de irrigação ou se a umidade do solo justifica o aumento."
                            .formatted(num(aguaSemana, 0), num((aguaSemana / aguaAnterior - 1) * 100, 0)),
                    null));
        }

        // 4. Concentração do gasto com insumos
        LocalDate hoje = agora.toLocalDate();
        Map<String, Double> porCategoria = insumos.custoPorCategoria(p.id(), hoje.minusDays(29), hoje.plusDays(1));
        double totalInsumos = porCategoria.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalInsumos > 0) {
            Map.Entry<String, Double> maior = porCategoria.entrySet().iterator().next();
            lista.add(new Recomendacao("BAIXA",
                    "Acompanhe o gasto com " + maior.getKey().toLowerCase(),
                    "A categoria %s responde por %s%% dos %s gastos com insumos nos últimos 30 dias. Compare as doses aplicadas com a recomendação da análise de solo."
                            .formatted(maior.getKey().toLowerCase(), num(maior.getValue() / totalInsumos * 100, 0),
                                    reais(totalInsumos)),
                    null));
        }

        if (lista.isEmpty()) {
            lista.add(new Recomendacao("INFO", "Tudo dentro do esperado",
                    "Nenhum desvio relevante de consumo foi encontrado nos últimos 7 dias.", null));
        }
        return lista;
    }
}
