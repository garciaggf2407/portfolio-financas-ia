package com.portfolio.financas.messaging;

import com.portfolio.financas.ai.AutoCategorizationApplier;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consome a fila 'transaction.categorization' e aplica a categorizacao via
 * IA a cada transacao (T-4.3.1). Deliberadamente nao captura excecoes: o
 * retry com backoff e o roteamento para a DLQ apos esgotar tentativas
 * (T-4.3.2) sao responsabilidade do adviceChain configurado em
 * RabbitConfig, que so funciona se a excecao propagar ate ele.
 */
@Component
public class CategorizationConsumer {

    private final AutoCategorizationApplier autoCategorizationApplier;

    public CategorizationConsumer(AutoCategorizationApplier autoCategorizationApplier) {
        this.autoCategorizationApplier = autoCategorizationApplier;
    }

    @RabbitListener(queues = "${categorization.messaging.queue:transaction.categorization}")
    public void onMessage(CategorizationMessage message) {
        autoCategorizationApplier.aplicar(message.transactionId());
    }
}
