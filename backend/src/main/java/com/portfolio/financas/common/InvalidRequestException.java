package com.portfolio.financas.common;

/**
 * Lancada quando o request e sintaticamente valido mas semanticamente
 * invalido (ex: parametro yearMonth fora do formato yyyy-MM). Traduzida
 * para HTTP 400 pelo GlobalExceptionHandler.
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
