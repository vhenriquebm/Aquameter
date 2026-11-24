package br.com.aquameter.service;

import java.text.NumberFormat;
import java.util.Locale;

/** Formatação de números no padrão brasileiro para mensagens exibidas ao produtor. */
public final class Formato {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private Formato() {
    }

    public static String num(double v, int casas) {
        NumberFormat f = NumberFormat.getNumberInstance(PT_BR);
        f.setMinimumFractionDigits(casas);
        f.setMaximumFractionDigits(casas);
        return f.format(v);
    }

    public static String reais(double v) {
        return "R$ " + num(v, 2);
    }

    public static double arred(double v, int casas) {
        double fator = Math.pow(10, casas);
        return Math.round(v * fator) / fator;
    }
}
