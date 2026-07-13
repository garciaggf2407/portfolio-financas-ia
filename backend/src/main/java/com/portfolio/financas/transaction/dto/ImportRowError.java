package com.portfolio.financas.transaction.dto;

/**
 * Erro de uma linha individual do CSV importado. Espelha o schema
 * ImportRowError em docs/openapi.yaml. `line` e 1-indexed e conta a
 * linha de header quando presente.
 */
public record ImportRowError(int line, String rawContent, String reason) {
}
