# SUMMARY — E-1 Fundação

**Status:** COMPLETED (com 1 gap de verificação — ver abaixo)

## Tasks

| Task | Commit | Status |
|------|--------|--------|
| T-1.1.1 | `489741f` feat(E1-T1.1.1) | DONE |
| T-1.2.1 | `99dd8bf` docs(E1-T1.2.1) | DONE |
| T-1.2.2 | `2a85515` feat(E1-T1.2.2) | DONE |
| T-1.2.3 | `69a4765` docs(E1-T1.2.3) | DONE |

## Verificado

- `./mvnw -q compile` — PASSED
- `npm install && npm run build` (frontend) — PASSED
- `docs/openapi.yaml` — lint 0 erros (@redocly/cli), válido (swagger-cli)
- `V1__init.sql` — parse de sintaxe OK (node-sql-parser, dialeto postgresql)

## Gap — FECHADO (2026-07-13, mesma sessão)

Docker Desktop foi instalado e o gap fechado com verificação real. Isso encontrou 2 bugs reais que a verificação de sintaxe não podia ver:

1. **Conflito de porta 5432** com um Postgres nativo já instalado como serviço do Windows — toda conexão ia parar no Postgres errado. Resolvido remapeando para porta 5433 (commit `07d9e4b`).
2. **`@Lob` incompatível** em `MonthlySummary.textoGeradoPorIa` (Hibernate 6 mapeia para `oid`, migration criou `text`) — `SchemaManagementException` no boot. Resolvido com `columnDefinition = "text"` (commit `07d9e4b`).

Confirmado rodando de verdade: `docker-compose up -d` (Postgres+RabbitMQ saudáveis), `./mvnw spring-boot:run` sobe limpo, Flyway valida a migration, Hibernate valida o schema, app responde na porta 8080. Testado com curl real: `GET /categories` (7 categorias seed), `POST /transactions/import` (2 transações importadas), `GET /summary/2026-07` (agregação correta, `categoria: null` funcionando).

## Decisões tomadas pelo agente

- `CategorizationStatusConverter` adicionado para reconciliar enum Java (UPPER_SNAKE_CASE) com valores lower_snake_case usados no ADR/schema — não estava explicitado nas tasks originais, mas necessário pra consistência.
- groupId `com.portfolio.financas`, artifactId `financas-ia`.

## Gate CP-1

**PASSED com ressalva** — ADR e contrato OpenAPI validados; runtime do docker-compose/migration pendente de verificação manual pelo operador quando Docker estiver instalado.
