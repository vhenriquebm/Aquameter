package br.com.aquameter.service;

import br.com.aquameter.domain.Medidor;
import br.com.aquameter.domain.TipoMedidor;

import java.util.Random;

/**
 * Perfis horários de consumo usados para simular sensores IoT enquanto o MVP não está ligado
 * a hidrômetros/medidores físicos. Os pesos indicam a fração do consumo diário em cada hora.
 */
public final class PerfilConsumo {

    /** Irrigação concentrada no início da manhã e no fim da tarde (avança no horário de ponta). */
    private static final double[] IRRIGACAO = {
            0.2, 0.2, 0.2, 0.2, 0.2, 6, 9, 9, 7, 2, 0.5, 0.5,
            0.5, 0.5, 0.5, 1, 5, 7, 8, 6, 1, 0.3, 0.2, 0.2};

    /** Galpão e sede: uso diurno com carga de base à noite. */
    private static final double[] GALPAO = {
            1, 1, 1, 1, 1, 1.5, 3, 5, 6, 6, 6, 5,
            4, 5, 6, 6, 5, 4, 4, 3.5, 3, 2, 1.5, 1};

    private static final double SOMA_IRRIGACAO = soma(IRRIGACAO);
    private static final double SOMA_GALPAO = soma(GALPAO);

    private PerfilConsumo() {
    }

    /** Fração do consumo diário esperada para a hora informada. */
    public static double fracaoHora(Medidor m, int hora) {
        if (usaPerfilIrrigacao(m)) {
            return IRRIGACAO[hora] / SOMA_IRRIGACAO;
        }
        return GALPAO[hora] / SOMA_GALPAO;
    }

    /** Medidores de água e a bomba de irrigação seguem o perfil de irrigação. */
    public static boolean usaPerfilIrrigacao(Medidor m) {
        return m.tipo() == TipoMedidor.AGUA || m.nome().toLowerCase().contains("bomba");
    }

    /** Leitura simulada de uma hora em operação normal (≈ 80% do limite diário, com ruído de ±15%). */
    public static double leituraHora(Medidor m, int hora, Random rnd) {
        double ruido = 0.85 + rnd.nextDouble() * 0.30;
        return Formato.arred(m.limiteDiario() * 0.80 * fracaoHora(m, hora) * ruido, 3);
    }

    private static double soma(double[] v) {
        double s = 0;
        for (double x : v) {
            s += x;
        }
        return s;
    }
}
