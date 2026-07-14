package com.portfolio.financas.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Publica uma mensagem por transacao a categorizar automaticamente
 * (T-4.3.1). O import (TransactionImportService) retorna assim que a
 * mensagem e aceita pelo broker -- nao espera a categorizacao terminar.
 */
@Component
public class CategorizationProducer {

    private final RabbitTemplate rabbitTemplate;

    public CategorizationProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishAll(List<UUID> transactionIds) {
        for (UUID transactionId : transactionIds) {
            rabbitTemplate.convertAndSend(
                    RabbitConfig.CATEGORIZATION_EXCHANGE,
                    RabbitConfig.CATEGORIZATION_ROUTING_KEY,
                    new CategorizationMessage(transactionId));
        }
    }
}
