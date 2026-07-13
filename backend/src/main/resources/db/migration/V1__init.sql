-- V1__init.sql
-- Modelo de dados financeiro inicial. Ver docs/adr/001-modelo-dados.md.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================
-- category
-- ============================================================
CREATE TABLE category (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome        VARCHAR(100) NOT NULL,
    tipo        VARCHAR(30)  NOT NULL,
    criado_em   TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT uk_category_nome UNIQUE (nome),
    CONSTRAINT ck_category_tipo CHECK (tipo IN ('DESPESA', 'RECEITA'))
);

-- ============================================================
-- transaction
-- ============================================================
CREATE TABLE transaction (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    data                   DATE           NOT NULL,
    descricao              VARCHAR(500)   NOT NULL,
    valor                  NUMERIC(12, 2) NOT NULL,
    categoria_id           UUID,
    origem_importacao      VARCHAR(100)   NOT NULL,
    status_categorizacao   VARCHAR(30)    NOT NULL DEFAULT 'sem_categoria',
    hash_deduplicacao      VARCHAR(64)    NOT NULL,
    criado_em              TIMESTAMP      NOT NULL DEFAULT now(),
    atualizado_em          TIMESTAMP      NOT NULL DEFAULT now(),

    CONSTRAINT fk_transaction_category
        FOREIGN KEY (categoria_id) REFERENCES category (id),
    CONSTRAINT ck_transaction_status_categorizacao
        CHECK (status_categorizacao IN ('sem_categoria', 'categorizada_manual', 'categorizada_ia')),
    CONSTRAINT uk_transaction_hash_deduplicacao UNIQUE (hash_deduplicacao)
);

-- Indice de suporte as agregacoes por mes (GET /summary/{yearMonth} e
-- /summary/history, T-2.3.1/T-2.3.2) e a listagem de transacoes ordenada
-- por data (GET /transactions).
CREATE INDEX idx_transaction_data ON transaction (data);

-- Indice de suporte a listagem/filtragem de transacoes por categoria
-- (ex: "mostrar todas as transacoes sem categoria").
CREATE INDEX idx_transaction_categoria_id ON transaction (categoria_id);

-- ============================================================
-- monthly_summary
-- ============================================================
CREATE TABLE monthly_summary (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mes                   VARCHAR(7)     NOT NULL,
    total_gasto           NUMERIC(12, 2) NOT NULL,
    texto_gerado_por_ia   TEXT,
    gerado_em             TIMESTAMP,

    CONSTRAINT uk_monthly_summary_mes UNIQUE (mes)
);

-- ============================================================
-- Seed de categorias padrao (usado por T-2.2.1)
-- ============================================================
INSERT INTO category (nome, tipo) VALUES
    ('Alimentacao', 'DESPESA'),
    ('Transporte',  'DESPESA'),
    ('Moradia',     'DESPESA'),
    ('Lazer',       'DESPESA'),
    ('Saude',       'DESPESA'),
    ('Outros',      'DESPESA'),
    ('Salario',     'RECEITA');
