package com.portfolio.financas.transaction;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Converte entre a convencao de nomes Java (UPPER_SNAKE_CASE, ex.
 * {@code SEM_CATEGORIA}) e a convencao usada no banco/ADR/OpenAPI
 * (lower_snake_case, ex. {@code sem_categoria} — ver
 * docs/adr/001-modelo-dados.md e V1__init.sql).
 */
@Converter(autoApply = true)
public class CategorizationStatusConverter
        implements AttributeConverter<CategorizationStatus, String> {

    @Override
    public String convertToDatabaseColumn(CategorizationStatus attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public CategorizationStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : CategorizationStatus.valueOf(dbData.toUpperCase());
    }
}
