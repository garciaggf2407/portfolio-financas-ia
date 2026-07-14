package com.portfolio.financas.ai;

/**
 * Falha ao chamar a Groq API: timeout, rate limit, status de erro HTTP ou
 * resposta malformada/vazia. Base para excecoes mais especificas (ex:
 * CategorizationApiException) capturaveis pelos consumidores de cada
 * funcionalidade (categorizacao via fila, resumo mensal via GET sincrono).
 */
public class GroqApiException extends RuntimeException {

    public GroqApiException(String message) {
        super(message);
    }

    public GroqApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
