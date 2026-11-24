'use strict';

const API = '/api';
const fmt = (v, casas = 0) => Number(v ?? 0).toLocaleString('pt-BR', {
    minimumFractionDigits: casas,
    maximumFractionDigits: casas
});
const reais = v => 'R$ ' + fmt(v, 2);
const $ = id => document.getElementById(id);
const CATEGORIAS = {FERTILIZANTE: 'Fertilizante', DEFENSIVO: 'Defensivo', SEMENTE: 'Semente', COMBUSTIVEL: 'Combustível', OUTRO: 'Outro'};

let propriedadeId = null;
let grafico = null;
let ultimoDashboard = null;

async function api(caminho, opcoes = {}) {
    const resp = await fetch(API + caminho, {
        headers: {'Content-Type': 'application/json'},
        ...opcoes
    });
    if (!resp.ok) {
        let detalhe = resp.statusText;
        try {
            detalhe = (await resp.json()).detail || detalhe;
        } catch (_) { /* sem corpo */ }
        throw new Error(detalhe);
    }
    const texto = await resp.text();
    return texto ? JSON.parse(texto) : null;
}

function toast(msg, erro = false) {
    const t = $('toast');
    t.textContent = msg;
    t.classList.toggle('erro', erro);
    t.classList.add('visivel');
    clearTimeout(toast.timer);
    toast.timer = setTimeout(() => t.classList.remove('visivel'), 5000);
}

function escapar(texto) {
    const div = document.createElement('div');
    div.textContent = texto ?? '';
    return div.innerHTML;
}

function variacaoHtml(pct, rotulo) {
    if (!pct) return `<span>igual à ${rotulo}</span>`;
    const classe = pct > 0 ? 'sobe' : 'desce';
    const seta = pct > 0 ? '▲' : '▼';
    return `<span class="${classe}">${seta} ${fmt(Math.abs(pct), 1)}%</span> vs. ${rotulo}`;
}

// ---------- Propriedades ----------
async function carregarPropriedades(selecionar) {
    const lista = await api('/propriedades');
    const sel = $('selPropriedade');
    sel.innerHTML = lista.map(p => `<option value="${p.id}">${escapar(p.nome)} — ${escapar(p.municipio)}/${p.uf}</option>`).join('');
    if (!lista.length) {
        $('titulo').textContent = 'Cadastre sua primeira propriedade';
        $('dlgPropriedade').showModal();
        return;
    }
    const salvo = Number(localStorageGet('propriedadeId'));
    propriedadeId = selecionar ?? (salvo || lista[0].id);
    if (!lista.some(p => p.id === propriedadeId)) propriedadeId = lista[0].id;
    sel.value = propriedadeId;
    await carregarTudo();
}

function localStorageGet(chave) {
    try {
        return localStorage.getItem(chave);
    } catch (_) {
        return null;
    }
}

function localStorageSet(chave, valor) {
    try {
        localStorage.setItem(chave, valor);
    } catch (_) { /* ignorado */ }
}

async function carregarTudo() {
    if (!propriedadeId) return;
    try {
        const [dash, insumos] = await Promise.all([
            api(`/propriedades/${propriedadeId}/dashboard`),
            api(`/propriedades/${propriedadeId}/insumos`)
        ]);
        renderDashboard(dash);
        renderInsumos(insumos, dash);
    } catch (e) {
        toast('Não foi possível carregar o painel: ' + e.message, true);
    }
}

// ---------- Dashboard ----------
function renderDashboard(d) {
    ultimoDashboard = d;
    const p = d.propriedade;
    const r = d.resumo;
    $('titulo').textContent = `Olá, ${p.produtor.split(' ')[0]} — ${p.nome}`;
    $('subtitulo').textContent = `${p.municipio}/${p.uf} · ${fmt(p.areaHa)} ha · ${p.culturaPrincipal || 'cultura não informada'}`;
    $('atualizadoEm').textContent = new Date(d.geradoEm).toLocaleTimeString('pt-BR', {hour: '2-digit', minute: '2-digit'});

    $('kpis').innerHTML = `
        <div class="kpi agua">
            <div class="rotulo">💧 Água hoje</div>
            <div class="valor">${fmt(r.aguaHoje, 1)}<small>m³</small></div>
            <div class="detalhe"><strong>${fmt(r.aguaHoje * 1000)} litros</strong> hoje</div>
            <div class="detalhe">7 dias: ${fmt(r.aguaSemana)} m³ · ${variacaoHtml(r.variacaoAguaPct, 'semana anterior')}</div>
        </div>
        <div class="kpi energia">
            <div class="rotulo">⚡ Energia hoje</div>
            <div class="valor">${fmt(r.energiaHoje)}<small>kWh</small></div>
            <div class="detalhe">7 dias: ${fmt(r.energiaSemana)} kWh · ${variacaoHtml(r.variacaoEnergiaPct, 'semana anterior')}</div>
        </div>
        <div class="kpi custo">
            <div class="rotulo">💰 Custo dos últimos 30 dias</div>
            <div class="valor">${reais(r.custoTotal30d)}</div>
            <div class="detalhe">Água ${reais(r.custoAgua30d)} · Energia ${reais(r.custoEnergia30d)} · Insumos ${reais(r.custoInsumos30d)}</div>
        </div>
        <div class="kpi alerta">
            <div class="rotulo">🔔 Alertas abertos</div>
            <div class="valor">${r.alertasAbertos}</div>
            <div class="detalhe">Economia possível: <strong>${reais(r.economiaPotencialMes)}/mês</strong></div>
        </div>`;

    $('recomendacoes').innerHTML = d.recomendacoes.map(rec => `
        <article class="rec ${rec.prioridade}">
            <span class="tag">Prioridade ${rec.prioridade.toLowerCase()}</span>
            <h3>${escapar(rec.titulo)}</h3>
            <p>${escapar(rec.descricao)}</p>
            ${rec.economiaMensalEstimada ? `<div class="economia">Economia estimada: ${reais(rec.economiaMensalEstimada)}/mês</div>` : ''}
        </article>`).join('');

    renderGrafico(d.serieDiaria);
    renderAlertas(d.alertasAbertos);
    renderMedidores(d.medidores);
}

function renderGrafico(serie) {
    const labels = serie.map(s => {
        const [, m, dia] = s.dia.split('-');
        return `${dia}/${m}`;
    });
    const dados = {
        labels,
        datasets: [
            {
                type: 'bar', label: 'Água (m³)', data: serie.map(s => s.agua),
                backgroundColor: 'rgba(37, 99, 168, .75)', borderRadius: 4, yAxisID: 'y', order: 2
            },
            {
                type: 'line', label: 'Água — média móvel 7 dias', data: serie.map(s => s.aguaMediaMovel7d),
                borderColor: '#11594a', borderWidth: 2, pointRadius: 0, tension: .3, yAxisID: 'y', order: 1
            },
            {
                type: 'line', label: 'Energia (kWh)', data: serie.map(s => s.energia),
                borderColor: '#b26b00', backgroundColor: '#b26b00', borderWidth: 2, pointRadius: 2,
                borderDash: [5, 4], tension: .3, yAxisID: 'y2', order: 0
            }
        ]
    };
    const opcoes = {
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        interaction: {mode: 'index', intersect: false},
        plugins: {
            legend: {position: 'bottom', labels: {boxWidth: 14, font: {size: 13}}},
            tooltip: {callbacks: {label: c => `${c.dataset.label}: ${fmt(c.parsed.y, 1)}`}}
        },
        scales: {
            x: {grid: {display: false}, ticks: {maxRotation: 0, autoSkip: true, maxTicksLimit: 10}},
            y: {beginAtZero: true, title: {display: true, text: 'm³'}, grid: {color: '#eef2f0'}},
            y2: {beginAtZero: true, position: 'right', title: {display: true, text: 'kWh'}, grid: {display: false}}
        }
    };
    if (grafico) {
        grafico.data = dados;
        grafico.update();
    } else {
        grafico = new Chart($('graficoConsumo'), {data: dados, options: opcoes});
    }
}

function renderAlertas(alertas) {
    $('contAlertas').textContent = alertas.length;
    if (!alertas.length) {
        $('alertas').innerHTML = '<li class="vazio">Nenhum alerta aberto. 👍</li>';
        return;
    }
    $('alertas').innerHTML = alertas.map(a => `
        <li>
            <div>
                <div class="tipo">${a.tipo === 'POSSIVEL_VAZAMENTO' ? 'Possível vazamento' : 'Acima do limite'}</div>
                <div class="msg">${escapar(a.mensagem)}</div>
                <div class="quando">${new Date(a.dataHora).toLocaleString('pt-BR', {dateStyle: 'short', timeStyle: 'short'})}</div>
            </div>
            <button class="btn btn-sec btn-mini" data-resolver="${a.id}">Resolver</button>
        </li>`).join('');
}

function renderMedidores(medidores) {
    if (!medidores.length) {
        $('medidores').innerHTML = '<p class="vazio">Nenhum medidor cadastrado. Clique em “+ Medidor”.</p>';
        return;
    }
    $('medidores').innerHTML = medidores.map(m => {
        const pct = Math.min(m.percentualLimite, 100);
        const classe = m.percentualLimite >= 100 ? 'critico' : m.percentualLimite >= 80 ? 'atencao' : '';
        const icone = m.tipo === 'AGUA' ? '💧' : '⚡';
        return `
        <div class="medidor">
            <div class="nome">${icone} ${escapar(m.nome)}</div>
            <div class="setor">${escapar(m.setor || '')}</div>
            <div class="numeros">
                <span><strong>${fmt(m.consumoHoje, 1)}</strong> de ${fmt(m.limiteDiario)} ${m.unidade}</span>
                <span class="${classe === 'critico' ? 'sobe' : ''}">${fmt(m.percentualLimite)}%</span>
            </div>
            <div class="barra"><span class="${classe}" style="width:${pct}%"></span></div>
        </div>`;
    }).join('');
}

// ---------- Resumo para WhatsApp ----------
function montarResumo(d) {
    const r = d.resumo;
    const linhas = [
        `*AquaMeter — ${d.propriedade.nome}*`,
        `Água hoje: ${fmt(r.aguaHoje, 1)} m³ (${fmt(r.aguaHoje * 1000)} litros)`,
        `Energia hoje: ${fmt(r.energiaHoje)} kWh`,
        `Alertas abertos: ${r.alertasAbertos}`,
        ''
    ];
    d.recomendacoes.forEach(rec => {
        linhas.push(`• ${rec.titulo}` + (rec.economiaMensalEstimada ? ` (economia de ${reais(rec.economiaMensalEstimada)}/mês)` : ''));
    });
    return linhas.join('\n');
}

async function copiarResumo() {
    if (!ultimoDashboard) return;
    const texto = montarResumo(ultimoDashboard);
    try {
        await navigator.clipboard.writeText(texto);
        toast('Resumo copiado. Cole no WhatsApp.');
    } catch (_) {
        window.open('https://wa.me/?text=' + encodeURIComponent(texto), '_blank');
    }
}

// ---------- Insumos ----------
function renderInsumos(lista, dash) {
    const cats = Object.entries(dash.insumosPorCategoria30d || {});
    const total = cats.reduce((s, [, v]) => s + v, 0);
    $('totalInsumos').textContent = total ? `${reais(total)} nos últimos 30 dias` : '';
    $('categorias').innerHTML = cats.map(([c, v]) => `<span>${CATEGORIAS[c] || c}: ${fmt(v / total * 100)}%</span>`).join('');
    $('listaInsumos').innerHTML = lista.length ? lista.map(i => `
        <tr>
            <td>${i.dataAplicacao.split('-').reverse().join('/')}</td>
            <td>${escapar(i.insumo)}</td>
            <td>${CATEGORIAS[i.categoria] || i.categoria}</td>
            <td class="num">${fmt(i.quantidade, i.quantidade % 1 ? 1 : 0)} ${escapar(i.unidade)}</td>
            <td class="num">${reais(i.custoTotal)}</td>
            <td>${escapar(i.setor || '')}</td>
        </tr>`).join('') : '<tr><td colspan="6" class="vazio">Nenhuma aplicação registrada.</td></tr>';
}

function dadosFormulario(form) {
    const dados = Object.fromEntries(new FormData(form).entries());
    for (const campo of ['quantidade', 'custoTotal', 'areaHa', 'tarifaAguaM3', 'tarifaEnergiaKwh', 'limiteDiario']) {
        if (campo in dados) dados[campo] = Number(dados[campo]);
    }
    return dados;
}

// ---------- Eventos ----------
function ligarEventos() {
    $('selPropriedade').addEventListener('change', e => {
        propriedadeId = Number(e.target.value);
        localStorageSet('propriedadeId', propriedadeId);
        carregarTudo();
    });

    $('alertas').addEventListener('click', async e => {
        const id = e.target.dataset.resolver;
        if (!id) return;
        e.target.disabled = true;
        try {
            await api(`/alertas/${id}/resolver`, {method: 'PATCH'});
            toast('Alerta marcado como resolvido.');
            await carregarTudo();
        } catch (err) {
            toast(err.message, true);
            e.target.disabled = false;
        }
    });

    const form = $('formInsumo');
    form.dataAplicacao.valueAsDate = new Date();
    form.addEventListener('submit', async e => {
        e.preventDefault();
        try {
            await api(`/propriedades/${propriedadeId}/insumos`, {method: 'POST', body: JSON.stringify(dadosFormulario(form))});
            form.reset();
            form.dataAplicacao.valueAsDate = new Date();
            toast('Aplicação registrada.');
            await carregarTudo();
        } catch (err) {
            toast(err.message, true);
        }
    });

    $('btnResumo').addEventListener('click', copiarResumo);

    $('btnNovaPropriedade').addEventListener('click', () => $('dlgPropriedade').showModal());
    $('dlgPropriedade').addEventListener('close', async () => {
        const dlg = $('dlgPropriedade');
        if (dlg.returnValue !== 'salvar') return;
        try {
            const nova = await api('/propriedades', {method: 'POST', body: JSON.stringify(dadosFormulario($('formPropriedade')))});
            $('formPropriedade').reset();
            localStorageSet('propriedadeId', nova.id);
            toast(`Propriedade “${nova.nome}” cadastrada. Agora adicione os medidores.`);
            await carregarPropriedades(nova.id);
        } catch (err) {
            toast(err.message, true);
        }
    });

    $('btnNovoMedidor').addEventListener('click', () => $('dlgMedidor').showModal());
    $('dlgMedidor').addEventListener('close', async () => {
        const dlg = $('dlgMedidor');
        if (dlg.returnValue !== 'salvar') return;
        try {
            await api(`/propriedades/${propriedadeId}/medidores`, {method: 'POST', body: JSON.stringify(dadosFormulario($('formMedidor')))});
            $('formMedidor').reset();
            toast('Medidor cadastrado.');
            await carregarTudo();
        } catch (err) {
            toast(err.message, true);
        }
    });

    const simular = async (botao, cenario) => {
        botao.disabled = true;
        try {
            const r = await api(`/simulador/propriedades/${propriedadeId}?cenario=${cenario}`, {method: 'POST'});
            const n = r.alertasGerados.length;
            toast(n ? `🔔 ${n} alerta(s): ${r.alertasGerados[0]}` : 'Leituras recebidas. Nenhum alerta novo.', n > 0);
            await carregarTudo();
        } catch (err) {
            toast(err.message, true);
        } finally {
            botao.disabled = false;
        }
    };
    $('btnSimNormal').addEventListener('click', e => simular(e.currentTarget, 'normal'));
    $('btnSimVazamento').addEventListener('click', e => simular(e.currentTarget, 'vazamento'));
}

ligarEventos();
carregarPropriedades().catch(e => toast('Falha ao conectar na API: ' + e.message, true));
setInterval(carregarTudo, 30000);
