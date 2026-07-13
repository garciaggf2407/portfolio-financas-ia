package com.portfolio.financas.category;

/**
 * Classificacao de uma categoria: usada para nao misturar agregacoes de
 * gasto com agregacoes de receita. Ver docs/adr/001-modelo-dados.md.
 */
public enum CategoryType {
    DESPESA,
    RECEITA
}
