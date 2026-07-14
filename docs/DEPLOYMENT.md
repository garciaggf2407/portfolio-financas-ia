# Deploy — portfolio-financas-ia

- **Escopo:** E-6 (Deploy + Docs), tasks T-6.1.1 (backend + DB + fila) e T-6.1.2 (frontend)
- **Free-tier escolhido:** Render (backend, via `render.yaml` na raiz do repo), Neon (Postgres), CloudAMQP (RabbitMQ), Vercel (frontend)

> Este guia assume execucao manual pelo operador (criacao de conta e
> conexao de secrets sao acoes que tocam sistemas externos e nao devem ser
> automatizadas sem supervisao). O que pode ser preparado sem conta —
> arquivos de config, ordem dos passos — ja esta pronto no repositorio.

## Por que essas 4 pecas (nao um unico provedor)

O stack tem 3 dependencias de infra (Postgres, RabbitMQ, app Java) mais o
frontend estatico. Nenhum free tier unico cobre as 4 com folga (ex.: Render
free nao inclui Postgres gerenciado com retencao aceitavel a longo prazo,
nem RabbitMQ). Separar por especialidade (Neon so faz Postgres, CloudAMQP so
faz fila) mantém cada peça no free tier certo, ao custo de mais um passo de
setup.

## 1. Banco de dados — Neon (ou Supabase)

1. Criar conta em [neon.tech](https://neon.tech), novo projeto Postgres 16.
2. Copiar a connection string (formato `postgresql://user:pass@host/db?sslmode=require`).
3. Guardar host/porta/db/user/senha separados — o Render pede como env vars
   individuais (`SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD`), nao uma
   URL unica.
4. Flyway (`backend/src/main/resources/db/migration`) roda automaticamente
   no boot da aplicacao contra esse banco — nao precisa rodar migration a
   mao.

## 2. Fila — CloudAMQP

1. Criar conta em [cloudamqp.com](https://www.cloudamqp.com), instancia
   "Little Lemur" (free).
2. A pagina da instancia da o host, vhost, user e senha via AMQPS (TLS) —
   por isso `render.yaml` ja define `SPRING_RABBITMQ_SSL_ENABLED=true`.

## 3. Backend — Render

`render.yaml` (raiz do repo) ja declara o servico (`runtime: docker`,
`rootDir: backend`, health check em `/actuator/health`). Passos:

1. Criar conta em [render.com](https://render.com), "New Blueprint",
   apontar para o repo `garciaggf2407/portfolio-financas-ia`.
2. Render le o `render.yaml` e cria o servico `financas-ia-api`
   automaticamente; as env vars marcadas `sync: false` (DB, fila, GROQ)
   ficam pendentes de preenchimento manual no painel.
3. Preencher: `SPRING_DATASOURCE_URL/_USERNAME/_PASSWORD` (Neon),
   `SPRING_RABBITMQ_HOST/_PORT/_USERNAME/_PASSWORD/_VIRTUAL_HOST`
   (CloudAMQP), `GROQ_API_KEY` (mesma chave usada localmente),
   `APP_CORS_ORIGIN` (URL do frontend no Vercel — preencher depois do
   passo 4, ou deixar `*` temporariamente e restringir depois).
4. Deploy inicial builda a imagem do `backend/Dockerfile`. Verificar
   `https://<servico>.onrender.com/actuator/health` retorna 200 antes de
   seguir.

## 4. Frontend — Vercel

O frontend ja le a URL do backend via `VITE_API_BASE`
(`frontend/src/api/client.ts:15`) — nao precisa de config nova no codigo,
so a env var no provedor.

1. Criar conta em [vercel.com](https://vercel.com), importar o mesmo repo.
2. Root Directory: `frontend`. Framework preset: Vite (deteccao automatica).
3. Env var: `VITE_API_BASE=https://<servico>.onrender.com`.
4. Deploy. Depois, voltar ao passo 3 do backend e setar `APP_CORS_ORIGIN`
   para a URL final do Vercel (evita deixar CORS aberto em `*` em
   producao).

## 5. Verificacao pos-deploy (CP-6)

- [x] `GET /actuator/health` do backend publico retorna 200 —
      https://financas-ia-api.onrender.com/actuator/health
- [x] Upload de um CSV de teste no frontend publico categoriza via IA
      (fila + Groq funcionando ponta a ponta contra infra gerenciada, nao
      so docker-compose local) — validado com extrato real (Nubank),
      27/27 transacoes importadas e categorizadas
- [x] `GET /admin/categorization/failures` acessivel (confirma RabbitMQ
      gerenciado conectado)
- [x] CORS liberado apenas para o dominio da Vercel (`APP_CORS_ORIGIN`),
      nao aberto em `*`

Demo publica: https://portfolio-financas-ia.vercel.app

## Notas de custo/limite (free tier)

- Neon free: projeto pausa apos inatividade prolongada — primeiro request
  apos pausa tem latencia de cold start (segundos, nao minutos).
- Render free: o servico web dorme apos ~15 min sem trafego; cold start
  medido na pratica foi **~80s**, nao os 30-60s estimados inicialmente.
  Sem mitigacao, a demo parece travada pra quem acessa fora de uso
  recente. Mitigado com `.github/workflows/keep-warm.yml` (ping em
  `/actuator/health` a cada 10min, 08h-20h BRT) e um aviso na UI de
  upload quando a requisicao passa de 5s.
- CloudAMQP free (Little Lemur): limite de ~1M mensagens/mes e poucas
  conexoes simultaneas — suficiente para o volume deste projeto. Note que
  a porta AMQPS (TLS) e sempre `5671`, nao a `5672` padrao sem TLS —
  `SPRING_RABBITMQ_PORT=5671` precisa ser setado explicitamente no Render
  junto com `SPRING_RABBITMQ_SSL_ENABLED=true`.
