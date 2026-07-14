package com.portfolio.financas.messaging.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Espelha o schema CategorizationFailure em docs/openapi.yaml.
 */
public record CategorizationFailureResponse(
        UUID transactionId, String descricao, int tentativas, String ultimoErro, LocalDateTime falhouEm) {
}
