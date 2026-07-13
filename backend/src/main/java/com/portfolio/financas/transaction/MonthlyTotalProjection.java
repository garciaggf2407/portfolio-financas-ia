package com.portfolio.financas.transaction;

import java.math.BigDecimal;

/**
 * Projecao Spring Data para o resultado de
 * TransactionRepository#sumByMonthBetween (query nativa SUM+GROUP BY,
 * T-2.3.2). Publica porque e consumida pelo pacote summary.
 */
public interface MonthlyTotalProjection {

    String getYearMonth();

    BigDecimal getTotal();
}
