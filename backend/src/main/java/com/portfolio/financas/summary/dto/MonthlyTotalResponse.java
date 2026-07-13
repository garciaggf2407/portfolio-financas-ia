package com.portfolio.financas.summary.dto;

import java.math.BigDecimal;

/**
 * Espelha o schema MonthlyTotal em docs/openapi.yaml. `total` e zero para
 * meses sem transacoes -- eles nunca sao omitidos (ver
 * SummaryHistoryService).
 */
public record MonthlyTotalResponse(String yearMonth, BigDecimal total) {
}
