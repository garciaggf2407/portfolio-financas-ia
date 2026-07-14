package com.portfolio.financas.summary;

import com.portfolio.financas.ai.MonthlySummaryService;
import com.portfolio.financas.common.ErrorResponse;
import com.portfolio.financas.common.InvalidRequestException;
import com.portfolio.financas.summary.dto.AiSummaryResponse;
import com.portfolio.financas.summary.dto.CategorySummaryItemResponse;
import com.portfolio.financas.summary.dto.CategorySummaryResponse;
import com.portfolio.financas.summary.dto.MonthlyTotalResponse;
import com.portfolio.financas.transaction.CategoryTotalProjection;
import com.portfolio.financas.transaction.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Agregacoes de gasto por categoria/mes. GET /summary/{yearMonth} usa
 * SUM+GROUP BY no banco (TransactionRepository#sumByCategoryForMonth),
 * nunca agregacao em memoria (ver acceptance criteria de T-2.3.1).
 * GET /summary/history (T-2.3.2) delega para SummaryHistoryService.
 * GET /summary/{yearMonth}/ai (T-4.2.1) delega para MonthlySummaryService.
 */
@RestController
public class SummaryController {

    private static final Pattern YEAR_MONTH = Pattern.compile("^\\d{4}-\\d{2}$");
    private static final int DEFAULT_HISTORY_MONTHS = 6;
    private static final int MIN_HISTORY_MONTHS = 1;
    private static final int MAX_HISTORY_MONTHS = 36;

    private final TransactionRepository transactionRepository;
    private final SummaryHistoryService summaryHistoryService;
    private final MonthlySummaryService monthlySummaryService;

    public SummaryController(TransactionRepository transactionRepository,
                              SummaryHistoryService summaryHistoryService,
                              MonthlySummaryService monthlySummaryService) {
        this.transactionRepository = transactionRepository;
        this.summaryHistoryService = summaryHistoryService;
        this.monthlySummaryService = monthlySummaryService;
    }

    @GetMapping("/summary/{yearMonth}")
    public CategorySummaryResponse getByCategory(@PathVariable String yearMonth) {
        validateYearMonth(yearMonth);

        List<CategoryTotalProjection> rows = transactionRepository.sumByCategoryForMonth(yearMonth);
        List<CategorySummaryItemResponse> itens = rows.stream()
                .map(CategorySummaryItemResponse::from)
                .toList();

        return new CategorySummaryResponse(yearMonth, itens);
    }

    @GetMapping("/summary/history")
    public List<MonthlyTotalResponse> getHistory(
            @RequestParam(name = "months", defaultValue = "" + DEFAULT_HISTORY_MONTHS) int months) {
        if (months < MIN_HISTORY_MONTHS || months > MAX_HISTORY_MONTHS) {
            throw new InvalidRequestException(
                    "months deve estar entre " + MIN_HISTORY_MONTHS + " e " + MAX_HISTORY_MONTHS
                            + ", recebido: " + months);
        }
        return summaryHistoryService.history(months);
    }

    @GetMapping("/summary/{yearMonth}/ai")
    public ResponseEntity<?> getAiSummary(@PathVariable String yearMonth) {
        validateYearMonth(yearMonth);

        return monthlySummaryService.getOrGenerate(yearMonth)
                .<ResponseEntity<?>>map(summary -> ResponseEntity.ok(AiSummaryResponse.from(summary)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.ACCEPTED)
                        .body(new ErrorResponse(HttpStatus.ACCEPTED.value(),
                                "Resumo ainda nao disponivel para " + yearMonth
                                        + " (sem transacoes no mes).")));
    }

    private void validateYearMonth(String yearMonth) {
        if (yearMonth == null || !YEAR_MONTH.matcher(yearMonth).matches()) {
            throw new InvalidRequestException(
                    "yearMonth invalido (esperado yyyy-MM): '" + yearMonth + "'");
        }
    }
}
