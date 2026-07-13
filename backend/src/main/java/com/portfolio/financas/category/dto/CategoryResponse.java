package com.portfolio.financas.category.dto;

import com.portfolio.financas.category.Category;
import com.portfolio.financas.category.CategoryType;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Espelha o schema Category em docs/openapi.yaml.
 */
public record CategoryResponse(UUID id, String nome, CategoryType tipo, LocalDateTime criadoEm) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(), category.getNome(), category.getTipo(), category.getCriadoEm());
    }
}
