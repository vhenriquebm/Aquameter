package br.com.aquameter.service;

import br.com.aquameter.domain.CategoriaInsumo;
import br.com.aquameter.domain.InsumoAplicacao;
import br.com.aquameter.domain.Medidor;
import br.com.aquameter.domain.Propriedade;
import br.com.aquameter.domain.TipoAlerta;
import br.com.aquameter.domain.TipoMedidor;
import br.com.aquameter.repository.AlertaRepository;
import br.com.aquameter.repository.InsumoRepository;
import br.com.aquameter.repository.LeituraRepository;
import br.com.aquameter.repository.LeituraRepository.LeituraLote;
import br.com.aquameter.repository.MedidorRepository;
import br.com.aquameter.repository.PropriedadeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Carga de demonstração: cria uma fazenda fictícia com 30 dias de leituras horárias, um vazamento
 * noturno no Talhão B nas últimas 3 noites, aplicações de insumos e o histórico de alertas.
 * Só roda quando o banco está vazio.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final int DIAS_HISTORICO = 30;
    private static final double VAZAMENTO_M3_HORA = 2.0;

    private final PropriedadeRepository propriedades;
    private final MedidorRepository medidores;
    private final LeituraRepository leituras;
    private final AlertaRepository alertas;
    private final InsumoRepository insumos;
    private final boolean ativo;

    public DataSeeder(PropriedadeRepository propriedades, MedidorRepository medidores, LeituraRepository leituras,
                      AlertaRepository alertas, InsumoRepository insumos,
                      @Value("${aquameter.seed.ativo:true}") boolean ativo) {
        this.propriedades = propriedades;
        this.medidores = medidores;
        this.leituras = leituras;
        this.alertas = alertas;
        this.insumos = insumos;
        this.ativo = ativo;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!ativo || propriedades.contar() > 0) {
            return;
        }
        log.info("Banco vazio: carregando dados de demonstração da Fazenda Santa Luzia...");

        long pid = propriedades.inserir(new Propriedade(null, "Fazenda Santa Luzia", "João Silva",
                "Mogi Mirim", "SP", 180, "Café e citros", 0.35, 0.68));

        Medidor talhaoA = criar(pid, "Hidrômetro irrigação - Talhão A (café)", TipoMedidor.AGUA, "Talhão A", 95);
        Medidor talhaoB = criar(pid, "Hidrômetro irrigação - Talhão B (citros)", TipoMedidor.AGUA, "Talhão B", 85);
        Medidor bomba = criar(pid, "Bomba do poço artesiano", TipoMedidor.ENERGIA, "Casa de bombas", 130);
        Medidor galpao = criar(pid, "Galpão e sede", TipoMedidor.ENERGIA, "Sede", 85);

        Random rnd = new Random(42);
        LocalDateTime agora = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        LocalDate hoje = agora.toLocalDate();
        LocalDateTime inicio = hoje.minusDays(DIAS_HISTORICO).atStartOfDay();
        LocalDate inicioVazamento = hoje.minusDays(2);

        List<LeituraLote> lote = new ArrayList<>();
        double fatorDia = 1;
        for (LocalDateTime t = inicio; !t.isAfter(agora); t = t.plusHours(1)) {
            int hora = t.getHour();
            if (hora == 0) {
                // dias mais quentes/secos irrigam mais; ocasionalmente passam do limite
                fatorDia = 0.88 + rnd.nextDouble() * 0.20 + (rnd.nextDouble() < 0.08 ? 0.32 : 0);
            }
            double a = PerfilConsumo.leituraHora(talhaoA, hora, rnd) * fatorDia;
            double b = PerfilConsumo.leituraHora(talhaoB, hora, rnd) * fatorDia;
            if (!t.toLocalDate().isBefore(inicioVazamento) && hora <= 4) {
                b += VAZAMENTO_M3_HORA * (0.9 + rnd.nextDouble() * 0.2);
            }
            double kwhBomba = (a + b) * RecomendacaoService.KWH_POR_M3_BOMBEADO + 0.4;
            double kwhGalpao = PerfilConsumo.leituraHora(galpao, hora, rnd);

            lote.add(new LeituraLote(talhaoA.id(), t, Formato.arred(a, 3)));
            lote.add(new LeituraLote(talhaoB.id(), t, Formato.arred(b, 3)));
            lote.add(new LeituraLote(bomba.id(), t, Formato.arred(kwhBomba, 3)));
            lote.add(new LeituraLote(galpao.id(), t, Formato.arred(kwhGalpao, 3)));
        }
        leituras.inserirLote(lote);

        gerarAlertasHistoricos(List.of(talhaoA, talhaoB, bomba, galpao), hoje);
        for (LocalDate d = inicioVazamento; !d.isAfter(hoje); d = d.plusDays(1)) {
            LocalDateTime quando = d.atTime(2, 0);
            if (!quando.isAfter(agora)) {
                double valor = Formato.arred(VAZAMENTO_M3_HORA + 0.25, 2);
                alertas.inserir(talhaoB.id(), quando, TipoAlerta.POSSIVEL_VAZAMENTO,
                        AlertaService.mensagemVazamento(talhaoB, valor), valor,
                        talhaoB.limiteDiario() * AlertaService.FRACAO_MADRUGADA_VAZAMENTO, false);
            }
        }

        inserirInsumos(pid, hoje);
        log.info("Dados de demonstração carregados: {} leituras.", lote.size());
    }

    private Medidor criar(long pid, String nome, TipoMedidor tipo, String setor, double limite) {
        Medidor m = new Medidor(null, pid, nome, tipo, setor, tipo.unidade(), limite, true);
        long id = medidores.inserir(m);
        return new Medidor(id, pid, nome, tipo, setor, tipo.unidade(), limite, true);
    }

    private void gerarAlertasHistoricos(List<Medidor> lista, LocalDate hoje) {
        for (Medidor m : lista) {
            Map<LocalDate, Double> totais = leituras.totaisDiarios(m.id(), hoje.minusDays(14));
            totais.forEach((dia, total) -> {
                if (total > m.limiteDiario()) {
                    boolean resolvido = dia.isBefore(hoje.minusDays(3));
                    alertas.inserir(m.id(), dia.atTime(19, 0), TipoAlerta.CONSUMO_ACIMA_LIMITE,
                            AlertaService.mensagemLimite(m, total), total, m.limiteDiario(), resolvido);
                }
            });
        }
    }

    private void inserirInsumos(long pid, LocalDate hoje) {
        Object[][] dados = {
                {27, "Calcário dolomítico", CategoriaInsumo.OUTRO, 10.0, "t", 1900.0, "Talhão B"},
                {24, "Ureia 45% N", CategoriaInsumo.FERTILIZANTE, 1200.0, "kg", 3360.0, "Talhão A"},
                {21, "Óleo diesel S10", CategoriaInsumo.COMBUSTIVEL, 600.0, "L", 3540.0, "Geral"},
                {17, "Fungicida (ferrugem do cafeeiro)", CategoriaInsumo.DEFENSIVO, 40.0, "L", 3200.0, "Talhão A"},
                {13, "Cloreto de potássio", CategoriaInsumo.FERTILIZANTE, 800.0, "kg", 2640.0, "Talhão B"},
                {9, "Inseticida (bicho-mineiro)", CategoriaInsumo.DEFENSIVO, 15.0, "L", 1650.0, "Talhão A"},
                {5, "Herbicida pós-emergente", CategoriaInsumo.DEFENSIVO, 30.0, "L", 1080.0, "Talhão B"},
                {2, "Adubo foliar (boro e zinco)", CategoriaInsumo.FERTILIZANTE, 50.0, "L", 1150.0, "Talhão A"},
        };
        for (Object[] d : dados) {
            insumos.inserir(pid, new InsumoAplicacao(null, pid, hoje.minusDays((Integer) d[0]), (String) d[1],
                    (CategoriaInsumo) d[2], (Double) d[3], (String) d[4], (Double) d[5], (String) d[6]));
        }
    }
}
