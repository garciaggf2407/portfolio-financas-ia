package com.portfolio.financas.transaction;

import java.util.List;
import java.util.UUID;

/**
 * Publicado por TransactionImportService apos persistir um lote de
 * transacoes (T-4.3.1). Desacopla o import da mensageria: o pacote
 * transaction nao conhece RabbitMQ, apenas anuncia "estas transacoes foram
 * importadas" via ApplicationEventPublisher; quem decide publicar na fila
 * de categorizacao e o listener em com.portfolio.financas.messaging.
 */
public record TransactionsImportedEvent(List<UUID> transactionIds) {
}
