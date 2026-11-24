package br.com.aquameter.service;

import br.com.aquameter.domain.Medidor;
import br.com.aquameter.domain.NovaLeitura;
import br.com.aquameter.domain.TipoMedidor;
import br.com.aquameter.repository.LeituraRepository;
import br.com.aquameter.repository.MedidorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Simula os sensores IoT de campo. Em produção, hidrômetros com pulso e medidores de energia
 * ligados a um gateway (ESP32/LoRaWAN) enviam as leituras para POST /api/leituras.
 */
@Service
public class SimuladorService {

    private static final Logger log = LoggerFactory.getLogger(SimuladorService.class);

    private final MedidorRepository medidores;
    private final LeituraRepository leituras;
    private final LeituraService leituraService;
    private final boolean ativo;
    private final Random rnd = new Random();

    public SimuladorService(MedidorRepository medidores, LeituraRepository leituras, LeituraService leituraService,
                            @Value("${aquameter.simulador.ativo:true}") boolean ativo) {
        this.medidores = medidores;
        this.leituras = leituras;
        this.leituraService = leituraService;
        this.ativo = ativo;
    }

    /** Execução periódica: uma leitura por medidor ativo (cada leitura representa ~1/60 de hora). */
    @Scheduled(initialDelayString = "${aquameter.simulador.intervalo-ms:60000}",
            fixedDelayString = "${aquameter.simulador.intervalo-ms:60000}")
    public void executarPeriodicamente() {
        if (!ativo) {
            return;
        }
        int hora = LocalDateTime.now().getHour();
        for (Medidor m : medidores.listarAtivos()) {
            double valor = Formato.arred(PerfilConsumo.leituraHora(m, hora, rnd) / 60.0, 3);
            leituraService.registrar(new NovaLeitura(m.id(), LocalDateTime.now(), valor));
        }
        log.debug("Simulador: leituras periódicas registradas");
    }

    /**
     * Gera leituras sob demanda para a demonstração.
     * cenario = "normal": uma hora de consumo típico em cada medidor da propriedade.
     * cenario = "vazamento": um pico no primeiro hidrômetro que ultrapassa o limite diário.
     */
    public List<String> simular(long propriedadeId, String cenario) {
        List<Medidor> lista = medidores.listarPorPropriedade(propriedadeId);
        if (lista.isEmpty()) {
            throw new NaoEncontradoException("Propriedade " + propriedadeId + " sem medidores cadastrados");
        }
        LocalDateTime agora = LocalDateTime.now();
        List<String> alertas = new ArrayList<>();
        if ("vazamento".equalsIgnoreCase(cenario)) {
            Medidor agua = lista.stream().filter(m -> m.tipo() == TipoMedidor.AGUA).findFirst()
                    .orElseThrow(() -> new NaoEncontradoException("Nenhum hidrômetro cadastrado"));
            double consumidoHoje = leituras.somaMedidor(agua.id(), agora.toLocalDate().atStartOfDay(), agora.plusSeconds(1));
            double pico = Math.max(agua.limiteDiario() * 0.25, agua.limiteDiario() * 1.15 - consumidoHoje);
            alertas.addAll(leituraService.registrar(new NovaLeitura(agua.id(), agora, Formato.arred(pico, 3))));
        } else {
            for (Medidor m : lista) {
                double valor = PerfilConsumo.leituraHora(m, agora.getHour(), rnd);
                alertas.addAll(leituraService.registrar(new NovaLeitura(m.id(), agora, valor)));
            }
        }
        return alertas;
    }
}
