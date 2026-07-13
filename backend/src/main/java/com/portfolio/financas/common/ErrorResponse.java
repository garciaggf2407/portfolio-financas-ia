package com.portfolio.financas.common;

import java.util.List;

/**
 * Corpo de erro padrao da API. Espelha o schema ErrorResponse em
 * docs/openapi.yaml.
 */
public record ErrorResponse(int status, String message, List<String> details) {

    public ErrorResponse(int status, String message) {
        this(status, message, null);
    }
}
