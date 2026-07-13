package com.portfolio.financas.transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Projecao Spring Data para o resultado de
 * TransactionRepository#sumByCategoryForMonth (query nativa SUM+GROUP BY,
 * T-2.3.1). Publica porque e consumida pelo pacote summary.
 */
public interface CategoryTotalProjection {

    UUID getId();

    String getNome();

    String getTipo();

    LocalDateTime getCriadoEm();

    BigDecimal getTotal();
}
