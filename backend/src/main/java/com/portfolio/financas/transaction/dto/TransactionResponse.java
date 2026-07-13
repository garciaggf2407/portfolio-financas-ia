package com.portfolio.financas.transaction.dto;

import com.portfolio.financas.category.dto.CategoryResponse;
import com.portfolio.financas.transaction.Transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

/**
 * Espelha o schema Transaction em docs/openapi.yaml. `statusCategorizacao`
 * e serializado em lower_snake_case (ex: "sem_categoria") para bater com
 * o enum do contrato -- CategorizationStatus em Java usa UPPER_SNAKE_CASE.
 */
public record TransactionResponse(
        UUID id,
        LocalDate data,
        String descricao,
        BigDecimal valor,
        CategoryResponse categoria,
        String origemImportacao,
        String statusCategorizacao) {

    public static TransactionResponse from(Transaction transaction) {
        CategoryResponse categoria = transaction.getCategoria() == null
                ? null
                : CategoryResponse.from(transaction.getCategoria());
        return new TransactionResponse(
                transaction.getId(),
                transaction.getData(),
                transaction.getDescricao(),
                transaction.getValor(),
                categoria,
                transaction.getOrigemImportacao(),
                transaction.getStatusCategorizacao().name().toLowerCase(Locale.ROOT));
    }
}
