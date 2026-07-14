package com.portfolio.financas.transaction;

import com.portfolio.financas.category.Category;
import com.portfolio.financas.category.CategoryRepository;
import com.portfolio.financas.common.InvalidRequestException;
import com.portfolio.financas.common.ResourceNotFoundException;
import com.portfolio.financas.transaction.dto.CategorizeTransactionRequest;
import com.portfolio.financas.transaction.dto.TransactionPageResponse;
import com.portfolio.financas.transaction.dto.TransactionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Listagem e categorizacao manual de transacoes. Categorizacao manual
 * sobrescreve qualquer categorizacao anterior -- incluindo categorizada_ia
 * -- pois a intervencao humana tem precedencia sobre a IA (ver
 * docs/openapi.yaml e ADR-001).
 */
@RestController
public class TransactionController {

    private static final Pattern YEAR_MONTH = Pattern.compile("^\\d{4}-\\d{2}$");
    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 200;
    private static final int DEFAULT_SIZE = 50;

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;

    public TransactionController(TransactionRepository transactionRepository,
                                  CategoryRepository categoryRepository) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
    }

    @GetMapping("/transactions")
    public TransactionPageResponse list(
            @RequestParam(required = false) String yearMonth,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_SIZE) int size) {

        if (yearMonth != null && !YEAR_MONTH.matcher(yearMonth).matches()) {
            throw new InvalidRequestException("yearMonth invalido (esperado yyyy-MM): '" + yearMonth + "'");
        }
        if (page < 0) {
            throw new InvalidRequestException("page deve ser >= 0, recebido: " + page);
        }
        if (size < MIN_SIZE || size > MAX_SIZE) {
            throw new InvalidRequestException(
                    "size deve estar entre " + MIN_SIZE + " e " + MAX_SIZE + ", recebido: " + size);
        }
        CategorizationStatus statusFilter = parseStatus(status);

        Page<Transaction> result = transactionRepository.findAllFiltered(
                yearMonth, statusFilter, PageRequest.of(page, size));
        return new TransactionPageResponse(
                result.getContent().stream().map(TransactionResponse::from).toList(),
                page,
                size,
                result.getTotalElements());
    }

    private CategorizationStatus parseStatus(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return CategorizationStatus.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("status invalido: '" + raw + "'");
        }
    }

    @PatchMapping("/transactions/{id}/category")
    public TransactionResponse categorize(@PathVariable UUID id,
                                           @RequestBody CategorizeTransactionRequest request) {
        if (request == null || request.categoriaId() == null) {
            throw new InvalidRequestException("categoriaId e obrigatorio.");
        }

        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transacao nao encontrada: " + id));
        Category category = categoryRepository.findById(request.categoriaId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Categoria nao encontrada: " + request.categoriaId()));

        transaction.categorizarManualmente(category);
        transactionRepository.save(transaction);

        return TransactionResponse.from(transaction);
    }
}
