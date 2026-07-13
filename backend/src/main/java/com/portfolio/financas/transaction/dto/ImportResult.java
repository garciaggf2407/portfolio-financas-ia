package com.portfolio.financas.transaction.dto;

import java.util.List;

/**
 * Resultado de um import de CSV. Espelha o schema ImportResult em
 * docs/openapi.yaml.
 */
public record ImportResult(int importadas, int ignoradasDuplicadas, List<ImportRowError> invalidas) {
}
