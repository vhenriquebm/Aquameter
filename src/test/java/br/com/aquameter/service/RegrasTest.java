package br.com.aquameter.service;

import br.com.aquameter.domain.Medidor;
import br.com.aquameter.domain.TipoMedidor;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testes das regras de negócio puras (não precisam de banco). */
class RegrasTest {

    private final Medidor talhao = new Medidor(1L, 1L, "Hidrômetro Talhão B", TipoMedidor.AGUA, "Talhão B", "m³", 85, true);
    private final Medidor galpao = new Medidor(2L, 1L, "Galpão", TipoMedidor.ENERGIA, "Sede", "kWh", 85, true);

    @Test
    void consumoAltoDeMadrugadaIndicaVazamento() {
        assertTrue(AlertaService.indicaVazamento(talhao, 2, 2.0));
    }

    @Test
    void consumoNormalDeMadrugadaNaoIndicaVazamento() {
        assertFalse(AlertaService.indicaVazamento(talhao, 2, 0.2));
    }

    @Test
    void irrigacaoDeDiaNaoIndicaVazamento() {
        assertFalse(AlertaService.indicaVazamento(talhao, 7, 9.0));
    }

    @Test
    void energiaNuncaGeraAlertaDeVazamento() {
        assertFalse(AlertaService.indicaVazamento(galpao, 2, 50));
    }

    @Test
    void perfilHorarioSomaUmDia() {
        double soma = 0;
        for (int h = 0; h < 24; h++) {
            soma += PerfilConsumo.fracaoHora(talhao, h);
        }
        assertEquals(1.0, soma, 1e-9);
    }

    @Test
    void diaSimuladoNormalFicaAbaixoDoLimite() {
        Random rnd = new Random(7);
        double total = 0;
        for (int h = 0; h < 24; h++) {
            total += PerfilConsumo.leituraHora(talhao, h, rnd);
        }
        assertTrue(total < talhao.limiteDiario(), "total=" + total);
    }

    @Test
    void variacaoPercentual() {
        assertEquals(10.0, DashboardService.variacao(110, 100));
        assertEquals(0.0, DashboardService.variacao(50, 0));
    }

    @Test
    void mensagemDeLimiteUsaFormatoBrasileiro() {
        String msg = AlertaService.mensagemLimite(talhao, 93.5);
        assertTrue(msg.contains("93,5 m³"), msg);
        assertTrue(msg.contains("10% acima"), msg);
    }
}
