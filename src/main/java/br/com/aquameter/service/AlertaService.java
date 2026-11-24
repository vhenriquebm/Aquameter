package br.com.aquameter.service;

import br.com.aquameter.domain.Medidor;
import br.com.aquameter.domain.TipoAlerta;
import br.com.aquameter.domain.TipoMedidor;
import br.com.aquameter.repository.AlertaRepository;
import br.com.aquameter.repository.LeituraRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static br.com.aquameter.service.Formato.num;

/** Regras de alerta avaliadas a cada leitura recebida. */
@Service
public class AlertaService {

    /** Consumo de água por hora na madrugada acima desta fração do limite diário indica possível vazamento. */
    static final double FRACAO_MADRUGADA_VAZAMENTO = 0.012;

    private final AlertaRepository alertas;
    private final LeituraRepository leituras;

    public AlertaService(AlertaRepository alertas, LeituraRepository leituras) {
        this.alertas = alertas;
        this.leituras = leituras;
    }

    public List<String> avaliar(Medidor m, LocalDateTime dataHora, double valor) {
        List<String> gerados = new ArrayList<>();
        LocalDateTime inicioDia = dataHora.toLocalDate().atStartOfDay();
        LocalDateTime fimDia = inicioDia.plusDays(1);

        double totalDia = leituras.somaMedidor(m.id(), inicioDia, fimDia);
        if (totalDia > m.limiteDiario()
                && !alertas.existe(m.id(), TipoAlerta.CONSUMO_ACIMA_LIMITE, inicioDia, fimDia)) {
            String msg = mensagemLimite(m, totalDia);
            alertas.inserir(m.id(), dataHora, TipoAlerta.CONSUMO_ACIMA_LIMITE, msg, totalDia, m.limiteDiario(), false);
            gerados.add(msg);
        }

        if (indicaVazamento(m, dataHora.getHour(), valor)
                && !alertas.existe(m.id(), TipoAlerta.POSSIVEL_VAZAMENTO, inicioDia, fimDia)) {
            String msg = mensagemVazamento(m, valor);
            double limiteHora = m.limiteDiario() * FRACAO_MADRUGADA_VAZAMENTO;
            alertas.inserir(m.id(), dataHora, TipoAlerta.POSSIVEL_VAZAMENTO, msg, valor, limiteHora, false);
            gerados.add(msg);
        }
        return gerados;
    }

    static boolean indicaVazamento(Medidor m, int hora, double valorHora) {
        return m.tipo() == TipoMedidor.AGUA
                && hora <= 4
                && valorHora > m.limiteDiario() * FRACAO_MADRUGADA_VAZAMENTO;
    }

    static String mensagemLimite(Medidor m, double totalDia) {
        double excesso = (totalDia / m.limiteDiario() - 1) * 100;
        return "%s consumiu %s %s hoje, %s%% acima do limite diário de %s %s."
                .formatted(m.nome(), num(totalDia, 1), m.unidade(), num(excesso, 0),
                        num(m.limiteDiario(), 0), m.unidade());
    }

    static String mensagemVazamento(Medidor m, double valorHora) {
        return "Consumo de %s %s/h na madrugada em %s, quando a irrigação está parada. Possível vazamento ou registro aberto."
                .formatted(num(valorHora, 2), m.unidade(), m.setor() == null ? m.nome() : m.setor());
    }
}
