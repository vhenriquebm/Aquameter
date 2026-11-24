package br.com.aquameter.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record Medidor(
        Long id,
        Long propriedadeId,
        @NotBlank String nome,
        @NotNull TipoMedidor tipo,
        String setor,
        String unidade,
        @Positive double limiteDiario,
        boolean ativo) {
}
