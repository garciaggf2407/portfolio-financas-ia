package com.portfolio.financas.messaging;

import com.portfolio.financas.common.InvalidRequestException;
import com.portfolio.financas.messaging.dto.CategorizationFailureResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * GET /admin/categorization/failures (T-4.3.2): inspeciona a dead-letter
 * queue 'transaction.categorization.dlq', usada quando a categorizacao via
 * IA falha repetidamente para uma transacao.
 */
@RestController
public class CategorizationFailureController {

    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 500;
    private static final int DEFAULT_LIMIT = 100;

    private final CategorizationFailureService categorizationFailureService;

    public CategorizationFailureController(CategorizationFailureService categorizationFailureService) {
        this.categorizationFailureService = categorizationFailureService;
    }

    @GetMapping("/admin/categorization/failures")
    public List<CategorizationFailureResponse> listFailures(
            @RequestParam(name = "limit", defaultValue = "" + DEFAULT_LIMIT) int limit) {
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            throw new InvalidRequestException(
                    "limit deve estar entre " + MIN_LIMIT + " e " + MAX_LIMIT + ", recebido: " + limit);
        }
        return categorizationFailureService.list(limit);
    }
}
