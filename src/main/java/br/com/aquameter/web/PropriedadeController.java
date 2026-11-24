package br.com.aquameter.web;

import br.com.aquameter.domain.Alerta;
import br.com.aquameter.domain.InsumoAplicacao;
import br.com.aquameter.domain.Medidor;
import br.com.aquameter.domain.Propriedade;
import br.com.aquameter.repository.AlertaRepository;
import br.com.aquameter.repository.InsumoRepository;
import br.com.aquameter.repository.MedidorRepository;
import br.com.aquameter.repository.PropriedadeRepository;
import br.com.aquameter.service.Dashboard;
import br.com.aquameter.service.DashboardService;
import br.com.aquameter.service.NaoEncontradoException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/propriedades")
public class PropriedadeController {

    private final PropriedadeRepository propriedades;
    private final MedidorRepository medidores;
    private final AlertaRepository alertas;
    private final InsumoRepository insumos;
    private final DashboardService dashboardService;

    public PropriedadeController(PropriedadeRepository propriedades, MedidorRepository medidores,
                                 AlertaRepository alertas, InsumoRepository insumos,
                                 DashboardService dashboardService) {
        this.propriedades = propriedades;
        this.medidores = medidores;
        this.alertas = alertas;
        this.insumos = insumos;
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public List<Propriedade> listar() {
        return propriedades.listar();
    }

    @GetMapping("/{id}")
    public Propriedade buscar(@PathVariable long id) {
        return existente(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Propriedade criar(@Valid @RequestBody Propriedade nova) {
        long id = propriedades.inserir(nova);
        return existente(id);
    }

    @GetMapping("/{id}/dashboard")
    public Dashboard dashboard(@PathVariable long id) {
        return dashboardService.montar(id);
    }

    @GetMapping("/{id}/medidores")
    public List<Medidor> listarMedidores(@PathVariable long id) {
        existente(id);
        return medidores.listarPorPropriedade(id);
    }

    @PostMapping("/{id}/medidores")
    @ResponseStatus(HttpStatus.CREATED)
    public Medidor criarMedidor(@PathVariable long id, @Valid @RequestBody Medidor novo) {
        existente(id);
        Medidor m = new Medidor(null, id, novo.nome(), novo.tipo(), novo.setor(), novo.tipo().unidade(),
                novo.limiteDiario(), true);
        long medidorId = medidores.inserir(m);
        return medidores.buscar(medidorId).orElseThrow();
    }

    @GetMapping("/{id}/alertas")
    public List<Alerta> listarAlertas(@PathVariable long id,
                                      @RequestParam(defaultValue = "false") boolean abertos) {
        existente(id);
        return alertas.listarPorPropriedade(id, abertos);
    }

    @GetMapping("/{id}/insumos")
    public List<InsumoAplicacao> listarInsumos(@PathVariable long id) {
        existente(id);
        return insumos.listar(id);
    }

    @PostMapping("/{id}/insumos")
    @ResponseStatus(HttpStatus.CREATED)
    public void registrarInsumo(@PathVariable long id, @Valid @RequestBody InsumoAplicacao nova) {
        existente(id);
        insumos.inserir(id, nova);
    }

    private Propriedade existente(long id) {
        return propriedades.buscar(id)
                .orElseThrow(() -> new NaoEncontradoException("Propriedade " + id + " não encontrada"));
    }
}
