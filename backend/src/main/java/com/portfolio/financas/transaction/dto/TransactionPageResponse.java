package com.portfolio.financas.transaction.dto;

import java.util.List;

/**
 * Espelha o schema TransactionPage em docs/openapi.yaml.
 */
public record TransactionPageResponse(List<TransactionResponse> content, int page, int size, long totalElements) {
}
