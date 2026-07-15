package com.portfolio.financas.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
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
    private final String exchangeName;
    private final String routingKey;

    public CategorizationProducer(
            RabbitTemplate rabbitTemplate,
            @Value("${categorization.messaging.exchange:financas.categorization.exchange}") String exchangeName,
            @Value("${categorization.messaging.routing-key:categorization}") String routingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchangeName = exchangeName;
        this.routingKey = routingKey;
    }

    public void publishAll(List<UUID> transactionIds) {
        for (UUID transactionId : transactionIds) {
            rabbitTemplate.convertAndSend(exchangeName, routingKey, new CategorizationMessage(transactionId));
        }
    }
}
