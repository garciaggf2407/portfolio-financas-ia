package com.portfolio.financas.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Uma linha de CSV que foi parseada com sucesso, ainda nao persistida.
 * `line` e mantido para rastreabilidade/depuracao.
 */
record ParsedTransactionRow(LocalDate data, String descricao, BigDecimal valor, int line) {
}
