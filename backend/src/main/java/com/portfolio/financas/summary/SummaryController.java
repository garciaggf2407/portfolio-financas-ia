package com.portfolio.financas.summary;

import com.portfolio.financas.common.InvalidRequestException;
import com.portfolio.financas.summary.dto.CategorySummaryItemResponse;
import com.portfolio.financas.summary.dto.CategorySummaryResponse;
import com.portfolio.financas.transaction.CategoryTotalProjection;
import com.portfolio.financas.transaction.TransactionRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Agregacoes de gasto por categoria/mes. GET /summary/{yearMonth} usa
 * SUM+GROUP BY no banco (TransactionRepository#sumByCategoryForMonth),
 * nunca agregacao em memoria (ver acceptance criteria de T-2.3.1).
 */
@RestController
public class SummaryController {

    private static final Pattern YEAR_MONTH = Pattern.compile("^\\d{4}-\\d{2}$");

    private final TransactionRepository transactionRepository;

    public SummaryController(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
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

    private void validateYearMonth(String yearMonth) {
        if (yearMonth == null || !YEAR_MONTH.matcher(yearMonth).matches()) {
            throw new InvalidRequestException(
                    "yearMonth invalido (esperado yyyy-MM): '" + yearMonth + "'");
        }
    }
}
