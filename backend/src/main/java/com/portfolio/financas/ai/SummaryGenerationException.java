package com.portfolio.financas.ai;

/**
 * Falha ao gerar o resumo mensal via IA (T-4.2.1) -- chamada sincrona a
 * Groq API dentro do GET /summary/{yearMonth}/ai (ver javadoc de
 * GroqApiException: resumo mensal nao passa pela fila). Traduzida por
 * GlobalExceptionHandler para HTTP 503, conforme docs/openapi.yaml.
 */
public class SummaryGenerationException extends GroqApiException {

    public SummaryGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
