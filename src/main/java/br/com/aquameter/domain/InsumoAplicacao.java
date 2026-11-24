package br.com.aquameter.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;

public record InsumoAplicacao(
        Long id,
        Long propriedadeId,
        @NotNull LocalDate dataAplicacao,
        @NotBlank String insumo,
        @NotNull CategoriaInsumo categoria,
        @Positive double quantidade,
        @NotBlank String unidade,
        @PositiveOrZero double custoTotal,
        String setor) {
}
