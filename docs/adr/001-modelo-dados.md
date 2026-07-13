# ADR-001: Modelo de dados financeiro

- **Status:** Aceito
- **Data:** 2026-07-13
- **Escopo:** E-1 (Fundacao) — task T-1.2.1
- **Decisores:** Operador do projeto

## Contexto

O sistema importa extratos bancarios em CSV, categoriza transacoes (manual e,
a partir da fase E-4, automaticamente via LLM) e gera agregacoes e resumos
mensais em linguagem natural. Precisamos de um modelo de dados que suporte:

1. Importacao idempotente de transacoes (sem duplicatas ao reimportar o
   mesmo extrato).
2. Categorizacao que possa ser manual **ou** automatica (IA), com
   rastreabilidade de qual delas foi aplicada por ultimo.
3. Categorias customizaveis pelo usuario (nao apenas uma lista fixa).
4. Agregacao eficiente de gasto por categoria e por mes.
5. Um resumo mensal gerado por IA, cacheado (nao gerado a cada requisicao).

Este documento fixa as entidades e o ciclo de vida de categorizacao antes da
implementacao das migrations (T-1.2.2) e do contrato OpenAPI (T-1.2.3), para
que ambos fiquem consistentes entre si.

## Decisao

Adotamos 3 entidades principais: `Transaction`, `Category` e
`MonthlySummary`.

### Entidade `Transaction`

Representa uma linha do extrato bancario, importada ou criada manualmente.

| Campo | Tipo | Descricao |
|---|---|---|
| `id` | UUID (PK) | Identificador unico. UUID em vez de sequence porque a transacao pode futuramente ser referenciada em mensagens assincronas (fila de categorizacao, E-4) sem vazar volume de dados (contagem de registros) e sem depender de uma unica instancia de banco gerando IDs sequenciais. |
| `data` | DATE | Data da transacao conforme o extrato (nao a data de importacao). Indexada — ver secao "Indices". |
| `descricao` | VARCHAR(500) | Descricao original da transacao, conforme veio no CSV. |
| `valor` | NUMERIC(12,2) | Valor da transacao. Positivo = receita, negativo = despesa. `NUMERIC` (nao `FLOAT`/`DOUBLE`) para evitar erros de arredondamento em valores monetarios. |
| `categoria_id` | UUID (FK, nullable) | Referencia a `Category`. Nullable porque uma transacao recem-importada comeca sem categoria (`sem_categoria`). |
| `origem_importacao` | VARCHAR(100) | Identifica de onde a transacao veio (ex: nome do arquivo CSV importado, ou `MANUAL` se criada manualmente). Usado tambem para auditoria/debug de imports. |
| `status_categorizacao` | VARCHAR(30) | Enum textual: `sem_categoria`, `categorizada_manual`, `categorizada_ia`. Ver "Ciclo de vida" abaixo. |
| `hash_deduplicacao` | VARCHAR(64) | Hash (SHA-256) de `data + descricao + valor`, usado para detectar duplicatas em reimportacao (T-2.1.2). Persistido (nao calculado em memoria a cada import) para permitir constraint `UNIQUE` no banco, que e a garantia mais forte contra duplicatas em cenarios de concorrencia. |
| `criado_em` | TIMESTAMP | Auditoria. |
| `atualizado_em` | TIMESTAMP | Auditoria — atualizado a cada mudanca de categoria. |

### Entidade `Category`

| Campo | Tipo | Descricao |
|---|---|---|
| `id` | UUID (PK) | Identificador unico. |
| `nome` | VARCHAR(100) | Nome da categoria (ex: "Alimentacao"). `UNIQUE` — categorias duplicadas por nome sao rejeitadas na criacao (T-2.2.1). |
| `tipo` | VARCHAR(30) | Classificacao da categoria: `DESPESA` ou `RECEITA`. Usado para nao misturar agregacoes de gasto com agregacoes de receita no dashboard. |
| `criado_em` | TIMESTAMP | Auditoria. |

Categorias padrao (Alimentacao, Transporte, Moradia, Lazer, Outros, ...) sao
inseridas via seed na propria migration Flyway (T-1.2.2), nao hardcoded no
codigo Java — assim o usuario pode edita-las/expandi-las sem redeploy.

### Entidade `MonthlySummary`

| Campo | Tipo | Descricao |
|---|---|---|
| `id` | UUID (PK) | Identificador unico. |
| `mes` | VARCHAR(7) | Formato `yyyy-MM` (ex: `2026-07`). Usado como chave de negocio — `UNIQUE`, um resumo por mes. String em vez de `DATE` porque o "mes" e uma chave de agregacao, nao um instante no tempo; evita ambiguidade de qual dia do mes representar. |
| `total_gasto` | NUMERIC(12,2) | Soma de todas as despesas do mes, persistida no momento da geracao do resumo (nao recalculada a cada leitura — ver `gerado_em`). |
| `texto_gerado_por_ia` | TEXT | Resumo em linguagem natural gerado pelo LLM (E-4/T-4.2.1). Nullable ate que a geracao aconteca (geracao e sob demanda, no primeiro `GET /summary/{yearMonth}/ai`). |
| `gerado_em` | TIMESTAMP | Momento em que o texto foi gerado. Usado para decidir se o resumo esta "cacheado e valido" ou precisa ser regenerado (regra de invalidacao de cache fica a cargo de E-4; nesta fase apenas o campo existe). |

`total_gasto` e as agregacoes por categoria (`GET /summary/{yearMonth}`) sao
calculadas via query agregada (`SUM` + `GROUP BY`) direto no banco (T-2.3.1),
**nao** armazenadas de forma denormalizada por categoria — `MonthlySummary`
guarda apenas o total do mes e o texto da IA, que sao os dois valores caros
de recalcular (o texto por envolver uma chamada de LLM).

### Ciclo de vida de categorizacao

```
        import de transacao (sem categoria)
                    │
                    ▼
            [ sem_categoria ]
               │         │
   PATCH manual│         │consumer da fila de IA (E-4)
   (usuario)   │         │categoriza automaticamente
               ▼         ▼
   [categorizada_manual] [categorizada_ia]
               │
               │ usuario pode recategorizar manualmente
               │ a qualquer momento (mesmo se ja categorizada_ia)
               ▼
      permanece categorizada_manual
```

Regras:

- Toda transacao nasce em `sem_categoria`.
- A categorizacao automatica via IA (E-4) **nunca sobrescreve** uma
  transacao ja marcada como `categorizada_manual` (regra de negocio validada
  em T-4.1.2) — a intervencao humana tem precedencia sobre a IA.
- A categorizacao manual (`PATCH /transactions/{id}/category`) sempre pode
  sobrescrever qualquer estado, incluindo `categorizada_ia`, porque o
  usuario e a fonte de verdade final.
- Nao existe transicao de volta para `sem_categoria` neste escopo — remover
  categoria fica fora do escopo do MVP (nao ha requisito para isso nas fases
  E-2/E-3/E-4 do blueprint).

## Alternativas consideradas

### Categoria como enum fixo no codigo vs. entidade propria

**Alternativa rejeitada:** modelar categoria como um `enum` Java
(`AeliMENTACAO, TRANSPORTE, MORADIA, LAZER, OUTROS`) persistido como string
na propria `Transaction`, sem tabela `category`.

**Por que foi rejeitada:**

- Um enum fixo exige alterar codigo + redeploy toda vez que o usuario quiser
  uma categoria nova (ex: "Assinaturas", "Pet"). Isso contradiz o objetivo do
  produto de ser uma ferramenta pessoal flexivel de financas.
- O enum nao comporta um campo `tipo` (DESPESA/RECEITA) por categoria sem
  logica auxiliar espalhada pelo codigo (ex: `switch` mapeando categoria →
  tipo).
- CRUD de categorias e um requisito explicito do blueprint (E-2/T-2.2.1: `GET
  /categories`, `POST /categories`), o que so faz sentido se categoria for
  uma entidade persistida, nao um enum.

**Trade-off aceito:** entidade propria introduz uma tabela e um join a mais
nas queries de transacao/agregacao, e uma validacao adicional (nome unico)
na criacao. Consideramos esse custo baixo frente ao ganho de flexibilidade,
dado o volume de dados esperado (uso pessoal, nao multi-tenant em escala).

### `status_categorizacao` como coluna string vs. tabela de eventos/historico

**Alternativa rejeitada, por ora:** modelar uma tabela
`transaction_categorization_event` guardando o historico completo de
mudancas de categoria (quem categorizou, quando, categoria anterior).

**Por que foi rejeitada (nesta fase):** nenhum requisito do blueprint (E-1 a
E-6) pede auditoria historica de recategorizacoes — apenas o estado atual
(`status_categorizacao`) e consultado pela UI e pelas regras de negocio
(T-4.1.2: nao sobrescrever categorizacao manual). Introduzir uma tabela de
eventos agora seria especulativo (violaria YAGNI). Se um requisito futuro
pedir historico, a coluna `atualizado_em` ja documentada serve de base para
essa extensao sem quebrar o schema atual.

## Consequencias

- Migrations Flyway (T-1.2.2) devem criar as 3 tabelas com as colunas acima,
  incluindo a `UNIQUE` constraint em `category.nome`, `UNIQUE` em
  `monthly_summary.mes`, e indice em `transaction.data` (usado pelas
  agregacoes por mes em T-2.3.1/T-2.3.2).
- O contrato OpenAPI (T-1.2.3) deve expor `status_categorizacao` como enum de
  3 valores (`sem_categoria`, `categorizada_manual`, `categorizada_ia`) e
  `category.tipo` como enum de 2 valores (`DESPESA`, `RECEITA`).
- `hash_deduplicacao` sera calculado no `TransactionImportService` (E-2) no
  momento do import, nao neste ADR — aqui apenas reservamos a coluna.
