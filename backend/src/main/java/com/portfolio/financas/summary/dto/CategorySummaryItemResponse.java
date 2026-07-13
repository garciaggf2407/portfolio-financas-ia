package com.portfolio.financas.summary.dto;

import com.portfolio.financas.category.CategoryType;
import com.portfolio.financas.category.dto.CategoryResponse;
import com.portfolio.financas.transaction.CategoryTotalProjection;

import java.math.BigDecimal;

/**
 * Espelha o schema CategorySummaryItem em docs/openapi.yaml. `categoria`
 * e nullable (decisao CP-2): representa transacoes sem categoria no mes.
 */
public record CategorySummaryItemResponse(CategoryResponse categoria, BigDecimal total) {

    public static CategorySummaryItemResponse from(CategoryTotalProjection projection) {
        if (projection.getId() == null) {
            return new CategorySummaryItemResponse(null, projection.getTotal());
        }
        CategoryResponse categoria = new CategoryResponse(
                projection.getId(),
                projection.getNome(),
                CategoryType.valueOf(projection.getTipo()),
                projection.getCriadoEm());
        return new CategorySummaryItemResponse(categoria, projection.getTotal());
    }
}
