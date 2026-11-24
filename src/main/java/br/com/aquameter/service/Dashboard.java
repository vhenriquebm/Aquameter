package br.com.aquameter.service;

import br.com.aquameter.domain.Alerta;
import br.com.aquameter.domain.Propriedade;
import br.com.aquameter.repository.LeituraRepository.PontoDiario;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record Dashboard(
        Propriedade propriedade,
        LocalDateTime geradoEm,
        Resumo resumo,
        List<PontoDiario> serieDiaria,
        List<MedidorHoje> medidores,
        List<Alerta> alertasAbertos,
        List<Recomendacao> recomendacoes,
        Map<String, Double> insumosPorCategoria30d) {

    public record Resumo(
            double aguaHoje,
            double energiaHoje,
            double aguaSemana,
            double aguaSemanaAnterior,
            double variacaoAguaPct,
            double energiaSemana,
            double energiaSemanaAnterior,
            double variacaoEnergiaPct,
            double custoAgua30d,
            double custoEnergia30d,
            double custoInsumos30d,
            double custoTotal30d,
            int alertasAbertos,
            double economiaPotencialMes) {
    }

    public record MedidorHoje(
            long id,
            String nome,
            String tipo,
            String setor,
            String unidade,
            double limiteDiario,
            double consumoHoje,
            double percentualLimite) {
    }
}
