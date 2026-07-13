package com.portfolio.financas.common;

/**
 * Lancada quando um recurso referenciado por id (transacao, categoria)
 * nao existe. Traduzida para HTTP 404 pelo GlobalExceptionHandler.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
