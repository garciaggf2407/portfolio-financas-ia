package com.portfolio.financas.common;

/**
 * Lancada quando uma criacao viola uma restricao de unicidade (ex: nome de
 * categoria repetido). Traduzida para HTTP 409 pelo GlobalExceptionHandler.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
