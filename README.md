# Portfolio Financas IA

Gestor de financas pessoais que importa extrato bancario em CSV e categoriza
transacoes automaticamente via IA (LLM). Projeto em construcao seguindo o
Blueprint `BP-2026-07-13-003` (ATHENA OS).

> Este README cobre apenas o setup de ambiente da fase E-1 (Fundacao). A
> documentacao completa de arquitetura, decisoes tecnicas e demo publica sera
> escrita na fase E-6 (`docs/DEPLOYMENT.md` e a secao de arquitetura deste
> arquivo).

## Stack

- **Backend:** Java 21, Spring Boot 3.5, Spring Data JPA, Flyway, Spring AMQP
- **Banco de dados:** PostgreSQL 16
- **Mensageria:** RabbitMQ 3.13 (categorizacao assincrona via IA, fase E-4)
- **Frontend:** React 19 + TypeScript + Vite, CSS Modules (sem bibliotecas de
  UI/HTTP externas — `fetch` nativo)

## Estrutura do repositorio

```
portfolio-financas-ia/
├── docker-compose.yml       # Postgres + RabbitMQ locais
├── backend/                 # Spring Boot (Maven)
│   └── src/main/java/com/portfolio/financas/
│       ├── transaction/     # import, CRUD e categorizacao manual (E-2)
│       ├── category/        # CRUD de categorias (E-2)
│       ├── summary/         # agregacoes de gasto por mes (E-2)
│       ├── ai/               # categorizacao e resumo via LLM (E-4)
│       └── messaging/       # producer/consumer RabbitMQ (E-4)
├── frontend/                 # React + Vite + TypeScript
│   └── src/
│       ├── pages/            # telas (Upload, Transacoes, Dashboard — E-3)
│       └── components/       # componentes reutilizaveis (E-3)
└── docs/
    ├── adr/                  # Architecture Decision Records
    └── openapi.yaml          # contrato de API
```

## Pre-requisitos

- Docker + Docker Compose
- Java 21 (JDK)
- Maven 3.9+ (ou use o wrapper `./mvnw` incluso em `backend/`)
- Node.js 20+ e npm 10+

## Setup local

### 1. Subir infraestrutura (Postgres + RabbitMQ)

```bash
docker-compose up -d
```

- Postgres disponivel em `localhost:5433` (db `financas_ia`, user/senha `financas`/`financas`) -- porta 5433, nao a padrao 5432, para nao conflitar com um Postgres nativo eventualmente instalado na maquina do desenvolvedor
- RabbitMQ AMQP em `localhost:5672`, management UI em [http://localhost:15672](http://localhost:15672) (user/senha `guest`/`guest`)

Para derrubar os containers preservando os volumes:

```bash
docker-compose down
```

### 2. Rodar o backend

```bash
cd backend
./mvnw spring-boot:run
```

A API sobe em `http://localhost:8080`. As migrations Flyway sao aplicadas
automaticamente no startup contra o Postgres do `docker-compose.yml`.

Para compilar sem rodar:

```bash
cd backend
./mvnw -q compile
```

### 3. Rodar o frontend

```bash
cd frontend
npm install
npm run dev
```

O frontend sobe em `http://localhost:5173` (padrao do Vite).

## Status do projeto

Este repositorio esta sendo construido de forma incremental via Blueprint
ATHENA OS. Fases E-1 (Fundacao), E-2 (Backend Core), E-3 (Frontend) e E-4
(IA + Mensageria) tem o codigo completo e verificado ponta a ponta contra
infraestrutura real (docker-compose + chave real da Groq API): import de
CSV, categorizacao automatica assincrona via LLM, resumo mensal em
linguagem natural com cache, e fila com retry/dead-letter queue.

E-4 aguarda **CP-4**, o checkpoint humano mais critico do blueprint: o
operador precisa revisar e conseguir explicar, sem consultar o codigo, por
que a categorizacao e assincrona e o que acontece quando o LLM falha (ver
`.planning/STATE.md`). E-5 (testes automatizados + CI) e E-6 (deploy
publico + documentacao final) ainda nao foram iniciados.

## Documentacao

- [`docs/adr/001-modelo-dados.md`](docs/adr/001-modelo-dados.md) — decisoes
  do modelo de dados financeiro (adicionado em E-1/T-1.2.1)
- [`docs/openapi.yaml`](docs/openapi.yaml) — contrato de API (adicionado em
  E-1/T-1.2.3)
