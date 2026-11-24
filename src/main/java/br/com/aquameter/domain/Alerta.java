package br.com.aquameter.domain;

import java.time.LocalDateTime;

public record Alerta(
        Long id,
        Long medidorId,
        String medidorNome,
        String setor,
        LocalDateTime dataHora,
        TipoAlerta tipo,
        String mensagem,
        Double valor,
        Double limite,
        boolean resolvido) {
}
