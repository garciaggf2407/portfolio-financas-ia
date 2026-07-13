package com.portfolio.financas.transaction;

import com.portfolio.financas.category.Category;
import com.portfolio.financas.category.CategoryRepository;
import com.portfolio.financas.common.InvalidRequestException;
import com.portfolio.financas.common.ResourceNotFoundException;
import com.portfolio.financas.transaction.dto.CategorizeTransactionRequest;
import com.portfolio.financas.transaction.dto.TransactionResponse;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Categorizacao manual de transacoes. Sobrescreve qualquer categorizacao
 * anterior -- incluindo categorizada_ia -- pois a intervencao humana tem
 * precedencia sobre a IA (ver docs/openapi.yaml e ADR-001).
 */
@RestController
public class TransactionController {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;

    public TransactionController(TransactionRepository transactionRepository,
                                  CategoryRepository categoryRepository) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
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
