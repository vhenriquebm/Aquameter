package br.com.aquameter.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record Propriedade(
        Long id,
        @NotBlank String nome,
        @NotBlank String produtor,
        @NotBlank String municipio,
        @NotBlank @Size(min = 2, max = 2) String uf,
        @Positive double areaHa,
        String culturaPrincipal,
        @PositiveOrZero double tarifaAguaM3,
        @PositiveOrZero double tarifaEnergiaKwh) {
}
