package com.portfolio.financas.messaging;

import com.portfolio.financas.transaction.TransactionsImportedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Ponte entre o dominio de import (transaction) e a mensageria (T-4.3.1).
 * O pacote transaction nao conhece RabbitMQ -- so publica
 * TransactionsImportedEvent via ApplicationEventPublisher; este listener
 * reage ao evento e enfileira uma mensagem de categorizacao por transacao.
 */
@Component
public class CategorizationImportListener {

    private final CategorizationProducer categorizationProducer;

    public CategorizationImportListener(CategorizationProducer categorizationProducer) {
        this.categorizationProducer = categorizationProducer;
    }

    @EventListener
    public void onTransactionsImported(TransactionsImportedEvent event) {
        categorizationProducer.publishAll(event.transactionIds());
    }
}
