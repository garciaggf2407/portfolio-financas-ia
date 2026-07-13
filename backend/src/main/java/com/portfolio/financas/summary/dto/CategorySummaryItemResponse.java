package com.portfolio.financas.summary.dto;

import com.portfolio.financas.category.CategoryType;
import com.portfolio.financas.category.dto.CategoryResponse;
import com.portfolio.financas.transaction.CategoryTotalProjection;

import java.math.BigDecimal;

/**
 * Espelha o schema CategorySummaryItem em docs/openapi.yaml.
 */
public record CategorySummaryItemResponse(CategoryResponse categoria, BigDecimal total) {

    public static CategorySummaryItemResponse from(CategoryTotalProjection projection) {
        CategoryResponse categoria = new CategoryResponse(
                projection.getId(),
                projection.getNome(),
                CategoryType.valueOf(projection.getTipo()),
                projection.getCriadoEm());
        return new CategorySummaryItemResponse(categoria, projection.getTotal());
    }
}
