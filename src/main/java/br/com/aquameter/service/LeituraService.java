package br.com.aquameter.service;

import br.com.aquameter.domain.Medidor;
import br.com.aquameter.domain.NovaLeitura;
import br.com.aquameter.repository.LeituraRepository;
import br.com.aquameter.repository.MedidorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Recebe leituras de sensores (ou do simulador), grava no Oracle e dispara a avaliação de alertas. */
@Service
public class LeituraService {

    private final LeituraRepository leituras;
    private final MedidorRepository medidores;
    private final AlertaService alertaService;

    public LeituraService(LeituraRepository leituras, MedidorRepository medidores, AlertaService alertaService) {
        this.leituras = leituras;
        this.medidores = medidores;
        this.alertaService = alertaService;
    }

    @Transactional
    public List<String> registrar(NovaLeitura nova) {
        Medidor m = medidores.buscar(nova.medidorId())
                .orElseThrow(() -> new NaoEncontradoException("Medidor " + nova.medidorId() + " não encontrado"));
        LocalDateTime quando = nova.dataHora() != null ? nova.dataHora() : LocalDateTime.now();
        leituras.inserir(m.id(), quando, nova.valor());
        return alertaService.avaliar(m, quando, nova.valor());
    }
}
