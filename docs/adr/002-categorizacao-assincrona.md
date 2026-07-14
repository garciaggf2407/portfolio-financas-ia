# ADR-002: Categorizacao assincrona via fila, com retry e dead-letter queue

- **Status:** Aceito
- **Data:** 2026-07-14
- **Escopo:** E-4 (IA + Mensageria) — tasks T-4.3.1, T-4.3.2
- **Decisores:** ATHENA (implementador), delegado pelo operador — ver nota em `.planning/STATE.md`, secao CP-4

## Contexto

A categorizacao automatica de uma transacao (T-4.1.1/T-4.1.2) depende de uma
chamada de rede a um LLM externo (Groq API): latencia de segundos, sujeita a
timeout e a rate limit do free tier. O import de um extrato CSV (T-2.1.1)
pode trazer dezenas ou centenas de transacoes de uma vez.

Chamar a IA de forma sincrona, dentro do proprio request HTTP de import,
teria dois problemas:

1. **Latencia do import cresceria linearmente** com o numero de transacoes
   importadas (N transacoes × latencia do LLM cada) — um extrato grande
   poderia levar minutos para responder, ou estourar timeout do cliente.
2. **Uma falha do LLM travaria o import inteiro.** Se a Groq API der rate
   limit na transacao 40 de 100, o que acontece com as outras 60? Tratar
   isso de forma sincrona exigiria logica de sucesso parcial dentro do
   proprio endpoint de import — misturando duas responsabilidades
   (persistir o extrato vs. categorizar via IA) numa unica operacao atomica
   que nao precisa ser atomica.

## Decisao

Desacoplamos import de categorizacao via fila (RabbitMQ), com uma politica
de retry e dead-letter explicita para falhas do LLM.

### Fluxo

```
POST /transactions/import
        │
        ▼
TransactionImportService.persist()
        │ (apos saveAll bem-sucedido)
        ▼
publica TransactionsImportedEvent (Spring ApplicationEvent, em processo)
        │
        ▼
CategorizationImportListener (pacote messaging)
        │ para cada transacao importada:
        ▼
CategorizationProducer publica CategorizationMessage{transactionId}
        │
        ▼
   fila 'transaction.categorization' (RabbitMQ, durable)
        │
        ▼
CategorizationConsumer (@RabbitListener)
        │
        ▼
AutoCategorizationApplier.aplicar(transactionId)
        │ chama TransactionCategorizationService (Groq API)
        ▼
   sucesso → transacao marcada categorizada_ia
   falha   → excecao propaga (ver "O que acontece na falha" abaixo)
```

O `POST /transactions/import` retorna assim que o lote e persistido e as
mensagens sao aceitas pelo broker — nao espera nenhuma categorizacao
terminar. O usuario ve as transacoes na tela como `sem_categoria` e elas
vao sendo atualizadas para `categorizada_ia` em segundos, de forma
assincrona (a UI nao tem long-polling/websocket para isso nesta fase —
aparece categorizada no proximo refresh/fetch da lista).

**Desacoplamento via evento de dominio, nao chamada direta:**
`TransactionImportService` publica `TransactionsImportedEvent` (um record
simples) via `ApplicationEventPublisher` do Spring — nao chama
`CategorizationProducer` diretamente. Isso mantem o pacote `transaction`
sem nenhuma dependencia de RabbitMQ; quem sabe que existe uma fila e o
pacote `messaging`. Se amanha a mensageria mudar de tecnologia (ex: Kafka),
so o listener muda, nao o servico de import.

### O que acontece quando a categorizacao de uma transacao falha

1. `CategorizationConsumer` **nao captura a excecao** — ela propaga
   deliberadamente. Toda a logica de retry/recovery fica fora do consumer,
   no `adviceChain` do listener container (`RabbitConfig`), para manter o
   consumer simples e a politica de retry centralizada e configuravel num
   unico lugar.
2. Um `RetryOperationsInterceptor` (Spring Retry) intercepta a excecao e
   tenta de novo, com backoff exponencial: por padrao 3 tentativas, com
   1s, 2s, 4s de espera entre elas (`categorization.retry.*` em
   `application.properties`).
3. Se as 3 tentativas falharem, um `RepublishMessageRecoverer` republica a
   mensagem original (mesmo payload — so o `transactionId`) na dead-letter
   queue `transaction.categorization.dlq`, anexando headers com o motivo do
   erro e o momento da falha. A mensagem **nao e perdida**.
4. A transacao em si permanece com `status_categorizacao = sem_categoria`
   — nenhuma escrita parcial ou inconsistente acontece, porque
   `AutoCategorizationApplier` so grava no banco *depois* de obter uma
   categoria valida da IA. Uma falha e sempre "nada aconteceu ainda", nunca
   um estado corrompido.
5. `GET /admin/categorization/failures` inspeciona a DLQ sem consumi-la
   (basic.get manual + nack com requeue, nao um `receive` destrutivo),
   resolvendo a descricao da transacao original para facilitar o
   diagnostico. Reprocessamento das mensagens da DLQ (mover de volta para a
   fila principal) **nao esta implementado** nesta fase — o endpoint e so
   de leitura; hoje a recuperacao seria manual (categorizar a transacao via
   `PATCH /transactions/{id}/category`).

## Alternativas consideradas

### Retry client-side (Spring Retry) vs. dead-lettering nativo do broker

**Alternativa rejeitada:** usar `x-dead-letter-exchange` do proprio
RabbitMQ com TTL/reject e deixar o broker "recontar" tentativas via o
header `x-death`.

**Por que foi rejeitada:** contar tentativas a partir do header `x-death`
e inspecionar o array de eventos de dead-letter e mais fragil e menos
explicito do que um `RetryTemplate` com uma politica de retry declarada em
codigo. O client-side tambem permite backoff exponencial de forma nativa
(`ExponentialBackOffPolicy`), enquanto o backoff via TTL do broker exigiria
uma fila intermediaria por tentativa (padrao "retry ladder"), bem mais
complexo para o ganho que traria aqui.

**Trade-off aceito:** se o processo da aplicacao cair no meio de uma
tentativa, o estado do retry (quantas tentativas ja foram feitas) nao
sobrevive — o Spring Retry conta em memoria, nao no broker. Para o volume e
criticidade deste projeto (portfolio pessoal, nao um sistema de pagamentos),
esse risco e aceitavel.

### Import sincrono com sucesso parcial vs. import assincrono

**Alternativa rejeitada:** manter o import sincrono, mas capturar falhas de
categorizacao por transacao e devolver um relatorio parcial
(`categorizadas: 95, falharam: 5`) na resposta do `POST /transactions/import`.

**Por que foi rejeitada:** acopla duas operacoes com taxas de falha e
latencia muito diferentes (persistir CSV: rapido e confiavel; chamar LLM:
lento e sujeito a rate limit) numa unica resposta HTTP. Tambem exigiria
que o cliente (frontend) tratasse um resultado misto logo apos o upload,
em vez de simplesmente mostrar a lista de transacoes recem-importadas e
deixa-las "chegar" categorizadas.

## Consequencias

- A fila e a DLQ sao infraestrutura adicional (RabbitMQ, ja presente via
  `docker-compose.yml` desde E-1) — nao ha custo de introduzir um novo
  componente de infra nesta fase.
- Testes de integracao reais do fluxo completo (import → fila → consumer →
  categorizacao, e o caminho de retry/DLQ) ficam para E-5 (Testcontainers,
  T-5.1.2/T-5.1.3) — este ADR cobre o design, nao a cobertura de teste
  desse design contra um broker real em CI.
- Reprocessamento automatico de mensagens na DLQ e um gap conhecido, nao
  implementado por nao ser exigido pelo escopo do blueprint (`T-4.3.2`
  pede apenas inspecao). Se vier a ser necessario, o endpoint de inspecao
  ja resolve a mensagem original completa, o que facilita adicionar um
  `POST /admin/categorization/failures/{transactionId}/retry` no futuro.
