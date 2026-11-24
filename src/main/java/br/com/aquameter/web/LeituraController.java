package br.com.aquameter.web;

import br.com.aquameter.domain.NovaLeitura;
import br.com.aquameter.service.LeituraService;
import br.com.aquameter.service.SimuladorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.aquameter.repository.AlertaRepository;
import br.com.aquameter.service.NaoEncontradoException;

import java.util.List;
import java.util.Map;

@RestController
public class LeituraController {

    private final LeituraService leituraService;
    private final SimuladorService simuladorService;
    private final AlertaRepository alertas;

    public LeituraController(LeituraService leituraService, SimuladorService simuladorService,
                             AlertaRepository alertas) {
        this.leituraService = leituraService;
        this.simuladorService = simuladorService;
        this.alertas = alertas;
    }

    /** Endpoint de ingestão usado pelos sensores IoT / gateway de campo. */
    @PostMapping("/api/leituras")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, List<String>> registrar(@Valid @RequestBody NovaLeitura leitura) {
        return Map.of("alertasGerados", leituraService.registrar(leitura));
    }

    /** Gera leituras simuladas para demonstração: cenario=normal | vazamento. */
    @PostMapping("/api/simulador/propriedades/{id}")
    public Map<String, List<String>> simular(@PathVariable long id,
                                             @RequestParam(defaultValue = "normal") String cenario) {
        return Map.of("alertasGerados", simuladorService.simular(id, cenario));
    }

    @PatchMapping("/api/alertas/{id}/resolver")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resolver(@PathVariable long id) {
        if (!alertas.resolver(id)) {
            throw new NaoEncontradoException("Alerta " + id + " não encontrado");
        }
    }
}
