package com.portfolio.financas.ai;

import com.portfolio.financas.category.Category;
import com.portfolio.financas.common.ResourceNotFoundException;
import com.portfolio.financas.transaction.Transaction;
import com.portfolio.financas.transaction.TransactionRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Aplica a categorizacao automatica via IA a uma transacao especifica
 * (T-4.1.2). Chamado pelo consumer da fila 'transaction.categorization'
 * (T-4.3.1) para cada mensagem recebida.
 *
 * Regra critica: transacao ja categorizada manualmente
 * (CATEGORIZADA_MANUAL) nunca e sobrescrita pela IA -- a intervencao humana
 * tem precedencia (ver docs/adr/001-modelo-dados.md e
 * CategorizationStatus).
 */
@Service
public class AutoCategorizationApplier {

    private final TransactionRepository transactionRepository;
    private final TransactionCategorizationService categorizationService;

    public AutoCategorizationApplier(TransactionRepository transactionRepository,
                                      TransactionCategorizationService categorizationService) {
        this.transactionRepository = transactionRepository;
        this.categorizationService = categorizationService;
    }

    /**
     * @param transactionId id da transacao a categorizar automaticamente
     * @throws ResourceNotFoundException se a transacao nao existir
     * @throws CategorizationApiException se a chamada a IA falhar -- deve
     *                                    propagar para o consumer acionar
     *                                    retry (T-4.3.2)
     */
    public void aplicar(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transacao nao encontrada: " + transactionId));

        if (transaction.isCategorizadaManualmente()) {
            return;
        }

        Category categoria = categorizationService.categorize(transaction.getDescricao());
        transaction.categorizarViaIa(categoria);
        transactionRepository.save(transaction);
    }
}
