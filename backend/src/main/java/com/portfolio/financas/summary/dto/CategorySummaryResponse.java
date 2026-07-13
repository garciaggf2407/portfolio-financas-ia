package com.portfolio.financas.summary.dto;

import java.util.List;

/**
 * Espelha o schema CategorySummary em docs/openapi.yaml. `itens` ja vem
 * ordenado do maior para o menor total (ordenacao feita no banco, ver
 * TransactionRepository#sumByCategoryForMonth).
 */
public record CategorySummaryResponse(String yearMonth, List<CategorySummaryItemResponse> itens) {
}
