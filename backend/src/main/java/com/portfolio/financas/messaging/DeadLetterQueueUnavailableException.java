package com.portfolio.financas.messaging;

/**
 * Falha ao consultar a dead-letter queue de categorizacao (T-4.3.2) --
 * RabbitMQ indisponivel ou fila inacessivel. Traduzida por
 * GlobalExceptionHandler para HTTP 503, conforme docs/openapi.yaml.
 */
public class DeadLetterQueueUnavailableException extends RuntimeException {

    public DeadLetterQueueUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
