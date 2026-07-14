package com.portfolio.financas.summary.dto;

import com.portfolio.financas.summary.MonthlySummary;

import java.time.LocalDateTime;

/**
 * Espelha o schema AiSummary em docs/openapi.yaml.
 */
public record AiSummaryResponse(String yearMonth, String texto, LocalDateTime geradoEm) {

    public static AiSummaryResponse from(MonthlySummary summary) {
        return new AiSummaryResponse(summary.getMes(), summary.getTextoGeradoPorIa(), summary.getGeradoEm());
    }
}
