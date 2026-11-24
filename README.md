# AquaMeter: MVP funcional

Plataforma de monitoramento de **água, energia e insumos** para pequenos e médios produtores rurais (50 a 500 ha).
Sensores de campo enviam leituras para uma API. O sistema grava tudo no **Oracle Database 23ai**, gera alertas (consumo acima do limite e possível vazamento) e mostra ao produtor recomendações com a economia estimada em reais.

## Como executar (recomendado: Docker)

Pré-requisito: [Docker Desktop](https://www.docker.com/products/docker-desktop/) instalado e aberto.

```bash
cd 1_MVP/aquameter        # pasta deste README
docker compose up --build
```

1. Na primeira execução, o Docker baixa a imagem do Oracle Database 23ai Free e a do Maven. Isso leva de 3 a 6 minutos.
2. Quando aparecer `Started AquaMeterApplication`, abra **http://localhost:8080**.
3. O banco é criado automaticamente (`src/main/resources/db/schema.sql`) e recebe uma fazenda de demonstração com 30 dias de histórico.

Para parar: `Ctrl+C`. Para apagar o banco e recomeçar do zero: `docker compose down -v`.

### Executar sem Docker para a aplicação (opcional)

Com JDK 21 e Maven instalados, suba só o banco e rode a aplicação localmente:

```bash
docker compose up -d oracle
mvn spring-boot:run
```

## Fluxo principal para a demonstração

| Passo | Onde | O que mostrar |
|---|---|---|
| 1 | Painel | KPIs de água, energia, custo de 30 dias e alertas abertos da Fazenda Santa Luzia |
| 2 | "O que fazer agora" | Recomendação de **possível vazamento no Talhão B** (consumo de madrugada nas últimas 3 noites) e de **energia no horário de ponta**, cada uma com a economia em R$/mês |
| 3 | Gráfico | Consumo diário de 30 dias com média móvel de 7 dias (calculada no Oracle com `AVG ... OVER`) |
| 4 | Simulador | **Simular rompimento de tubulação**: a leitura entra pela API, o limite diário do Talhão A estoura e um alerta novo aparece na hora |
| 5 | Alertas | **Resolver** um alerta |
| 6 | Insumos | Registrar uma aplicação (ex.: 300 kg de sulfato de amônio, R$ 870) e ver o custo por categoria se atualizar |
| 7 | + Propriedade / + Medidor | Cadastrar uma propriedade nova e um medidor (fluxo de onboarding de um cliente) |

O simulador também gera uma leitura por medidor a cada 60 s em segundo plano (`SIMULADOR_ATIVO`), então o painel continua mudando durante a apresentação.

Para enviar uma leitura como um sensor real faria:

```bash
python3 ferramentas/sensor_simulado.py --medidor 1 --valor 3.2
# ou
curl -X POST http://localhost:8080/api/leituras -H "Content-Type: application/json" \
     -d '{"medidorId": 2, "valor": 2.4, "dataHora": "2026-11-24T02:00:00"}'
```

## Arquitetura

```
[Sensores / gateway IoT]──POST /api/leituras──┐
[Simulador interno]───────────────────────────┤
                                              ▼
                    Spring Boot 3.5 (Java 21)
                    ├─ LeituraService → AlertaService (regras de limite e vazamento)
                    ├─ DashboardService + RecomendacaoService (economia em R$)
                    └─ Repositórios JDBC (SQL Oracle)
                                              ▼
                    Oracle Database 23ai (Free em Docker / Autonomous Database na OCI)
                    tabelas: propriedade, medidor, leitura, alerta, insumo_aplicacao
                    view: vw_consumo_diario
                                              ▼
                    Dashboard web (HTML + JS + Chart.js) servido pelo próprio Spring Boot
```

### Uso da Oracle

- **Oracle Database 23ai Free** como banco do MVP, rodando em container (`gvenzl/oracle-free`).
- Recursos do 23ai usados no schema: `CREATE TABLE IF NOT EXISTS`, tipo `BOOLEAN` nativo e colunas `IDENTITY`.
- SQL analítico do Oracle: view `vw_consumo_diario` com `TRUNC`, média móvel com `AVG(...) OVER (ROWS BETWEEN 6 PRECEDING AND CURRENT ROW)`, filtros com `EXTRACT(HOUR ...)` e paginação com `FETCH FIRST`.
- Driver oficial `ojdbc11`.
- **Caminho para produção:** trocar `DB_URL` para um **OCI Autonomous Database** (mesmo SQL, sem mudança de código) e publicar a aplicação no **OCI Container Instances**. Veja o documento consolidado.

## API

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/propriedades` | Lista propriedades |
| POST | `/api/propriedades` | Cadastra propriedade |
| GET | `/api/propriedades/{id}/dashboard` | KPIs, série diária, medidores, alertas e recomendações |
| GET / POST | `/api/propriedades/{id}/medidores` | Lista / cadastra medidores |
| GET | `/api/propriedades/{id}/alertas?abertos=true` | Alertas |
| GET / POST | `/api/propriedades/{id}/insumos` | Lista / registra aplicação de insumo |
| POST | `/api/leituras` | Ingestão de leitura (`medidorId`, `valor`, `dataHora` opcional) |
| PATCH | `/api/alertas/{id}/resolver` | Marca alerta como resolvido |
| POST | `/api/simulador/propriedades/{id}?cenario=normal\|vazamento` | Gera leituras de demonstração |

## Testes

```bash
mvn test
```

`RegrasTest` cobre as regras de negócio puras (detecção de vazamento, perfil de consumo, variação percentual e formatação das mensagens).

## Configuração

| Variável | Padrão | Uso |
|---|---|---|
| `DB_URL` | `jdbc:oracle:thin:@//localhost:1521/FREEPDB1` | URL JDBC do Oracle |
| `DB_USER` / `DB_PASSWORD` | `aquameter` / `aquameter` | Credenciais |
| `SEED_ATIVO` | `true` | Carrega a fazenda de demonstração quando o banco está vazio |
| `SIMULADOR_ATIVO` | `true` | Gera leituras periódicas |
| `SIMULADOR_INTERVALO_MS` | `60000` | Intervalo do simulador |

## Problemas comuns

- **Porta 1521 ou 8080 ocupada:** altere o lado esquerdo do mapeamento em `docker-compose.yml` (ex.: `"8081:8080"`).
- **A aplicação sobe antes do banco:** o compose espera o healthcheck do Oracle. Se o seu Docker for antigo, rode `docker compose up -d oracle`, aguarde 1 minuto e depois `docker compose up --build app`.
- **Mac com Apple Silicon:** a imagem `gvenzl/oracle-free:23-slim-faststart` tem versão ARM64 nativa.
