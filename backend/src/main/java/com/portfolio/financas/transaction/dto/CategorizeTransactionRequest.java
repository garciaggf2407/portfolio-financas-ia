package com.portfolio.financas.transaction.dto;

import java.util.UUID;

/**
 * Espelha o schema CategorizeTransactionRequest em docs/openapi.yaml.
 */
public record CategorizeTransactionRequest(UUID categoriaId) {
}
