package com.portfolio.financas.messaging;

import com.portfolio.financas.transaction.TransactionsImportedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CategorizationImportListenerTest {

    @Mock
    private CategorizationProducer categorizationProducer;

    @Test
    void eventoDeImportacaoPublicaUmaMensagemPorTransacao() {
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());
        CategorizationImportListener listener = new CategorizationImportListener(categorizationProducer);

        listener.onTransactionsImported(new TransactionsImportedEvent(ids));

        verify(categorizationProducer).publishAll(ids);
    }
}
