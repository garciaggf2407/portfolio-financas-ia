package com.portfolio.financas.ai;

/**
 * Falha especifica ao categorizar uma transacao via IA -- inclui falhas de
 * chamada a Groq API (ver GroqApiException) e o caso em que a IA retorna um
 * texto que nao corresponde a nenhuma categoria existente. Capturada pelo
 * consumer da fila de categorizacao (T-4.3.1/T-4.3.2) para acionar retry.
 */
public class CategorizationApiException extends GroqApiException {

    public CategorizationApiException(String message) {
        super(message);
    }

    public CategorizationApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
