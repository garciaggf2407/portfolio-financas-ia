package com.portfolio.financas.messaging;

import com.portfolio.financas.ai.AutoCategorizationApplier;
import com.portfolio.financas.ai.CategorizationApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CategorizationConsumerTest {

    @Mock
    private AutoCategorizationApplier autoCategorizationApplier;

    @Test
    void mensagemValidaDelegaParaOApplier() {
        UUID transactionId = UUID.randomUUID();
        CategorizationConsumer consumer = new CategorizationConsumer(autoCategorizationApplier);

        consumer.onMessage(new CategorizationMessage(transactionId));

        verify(autoCategorizationApplier).aplicar(transactionId);
    }

    /**
     * O consumer nao deve engolir a excecao: e ela propagando ate o
     * adviceChain do listener container que aciona o retry com backoff
     * (T-4.3.2, ver RabbitConfig).
     */
    @Test
    void falhaDoApplierPropagaParaOListenerContainer() {
        UUID transactionId = UUID.randomUUID();
        doThrow(new CategorizationApiException("Groq API indisponivel."))
                .when(autoCategorizationApplier).aplicar(transactionId);
        CategorizationConsumer consumer = new CategorizationConsumer(autoCategorizationApplier);

        assertThatThrownBy(() -> consumer.onMessage(new CategorizationMessage(transactionId)))
                .isInstanceOf(CategorizationApiException.class);
    }
}
