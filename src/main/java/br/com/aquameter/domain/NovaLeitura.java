package br.com.aquameter.domain;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDateTime;

/** Payload enviado por um sensor/gateway IoT (ou pelo simulador). dataHora é opcional: padrão = agora. */
public record NovaLeitura(
        @NotNull Long medidorId,
        LocalDateTime dataHora,
        @PositiveOrZero double valor) {
}
