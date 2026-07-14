package com.portfolio.financas.messaging;

import java.util.UUID;

/**
 * Payload publicado na fila 'transaction.categorization' (T-4.3.1).
 * Serializado como JSON (Jackson2JsonMessageConverter, ver RabbitConfig)
 * para ficar inspecionavel na management UI do RabbitMQ
 * (localhost:15672), em vez de um blob de serializacao Java.
 */
public record CategorizationMessage(UUID transactionId) {
}
