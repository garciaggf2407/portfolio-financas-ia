package com.portfolio.financas.category.dto;

import com.portfolio.financas.category.CategoryType;

/**
 * Espelha o schema CreateCategoryRequest em docs/openapi.yaml. Sem
 * anotacoes de bean validation: o pom.xml nao inclui
 * spring-boot-starter-validation, entao a validacao de `nome`/`tipo` e
 * feita manualmente em CategoryController.
 */
public record CreateCategoryRequest(String nome, CategoryType tipo) {
}
