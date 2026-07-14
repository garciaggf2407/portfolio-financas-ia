package com.portfolio.financas.transaction;

import com.portfolio.financas.transaction.dto.ImportRowError;

import java.util.List;

/**
 * Resultado de parsing de um extrato bancario, independente do formato de
 * origem (CSV, OFX). CsvTransactionParser e OfxTransactionParser retornam
 * este mesmo tipo para que TransactionImportService trate persistencia e
 * deduplicacao de forma identica para ambos.
 */
record ParseOutcome(List<ParsedTransactionRow> validRows, List<ImportRowError> invalidRows) {
}
