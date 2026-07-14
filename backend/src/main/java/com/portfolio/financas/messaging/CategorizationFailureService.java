package com.portfolio.financas.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.financas.messaging.dto.CategorizationFailureResponse;
import com.portfolio.financas.transaction.TransactionRepository;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.LongString;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Le mensagens da dead-letter queue de categorizacao (T-4.3.2) sem
 * remove-las: basic.get com ack manual seguido de nack(requeue=true) para
 * cada mensagem lida -- e uma inspecao, nao um consumo. Usado apenas pelo
 * endpoint administrativo GET /admin/categorization/failures.
 */
@Service
class CategorizationFailureService {

    private final RabbitTemplate rabbitTemplate;
    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;

    CategorizationFailureService(RabbitTemplate rabbitTemplate,
                                  TransactionRepository transactionRepository,
                                  ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.transactionRepository = transactionRepository;
        this.objectMapper = objectMapper;
    }

    List<CategorizationFailureResponse> list(int limit) {
        try {
            List<GetResponse> raw = rabbitTemplate.execute(channel -> {
                long available = channel.queueDeclarePassive(RabbitConfig.CATEGORIZATION_DLQ).getMessageCount();
                int toRead = (int) Math.min(limit, available);

                List<GetResponse> responses = new ArrayList<>(toRead);
                for (int i = 0; i < toRead; i++) {
                    GetResponse response = channel.basicGet(RabbitConfig.CATEGORIZATION_DLQ, false);
                    if (response == null) {
                        break;
                    }
                    responses.add(response);
                }
                // Devolve as mensagens para a fila -- inspecao nao-destrutiva.
                for (GetResponse response : responses) {
                    channel.basicNack(response.getEnvelope().getDeliveryTag(), false, true);
                }
                return responses;
            });

            List<CategorizationFailureResponse> failures = new ArrayList<>(raw.size());
            for (GetResponse response : raw) {
                failures.add(toFailureResponse(response));
            }
            return failures;
        } catch (AmqpException | IOException e) {
            throw new DeadLetterQueueUnavailableException(
                    "Nao foi possivel consultar a dead-letter queue de categorizacao.", e);
        }
    }

    private CategorizationFailureResponse toFailureResponse(GetResponse response) throws IOException {
        CategorizationMessage message = objectMapper.readValue(response.getBody(), CategorizationMessage.class);
        AMQP.BasicProperties props = response.getProps();
        Map<String, Object> headers = props.getHeaders() != null ? props.getHeaders() : Map.of();

        String descricao = transactionRepository.findById(message.transactionId())
                .map(transaction -> transaction.getDescricao())
                .orElse("(transacao nao encontrada)");
        int tentativas = headerAsInt(headers.get(RabbitConfig.HEADER_ATTEMPTS));
        String ultimoErro = headerAsString(headers.get(RepublishMessageRecoverer.X_EXCEPTION_MESSAGE));
        LocalDateTime falhouEm = parseFailedAt(headerAsString(headers.get(RabbitConfig.HEADER_FAILED_AT)));

        return new CategorizationFailureResponse(
                message.transactionId(),
                descricao,
                tentativas,
                ultimoErro != null ? ultimoErro : "motivo desconhecido",
                falhouEm);
    }

    private int headerAsInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    /**
     * Headers lidos via basic.get "cru" (fora da conversao automatica do
     * Spring AMQP) chegam como com.rabbitmq.client.LongString, nao String
     * -- diferente de headers lidos via abstracoes do Spring (que ja
     * convertem). toString() cobre os dois casos.
     */
    private String headerAsString(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof LongString longString ? longString.toString() : value.toString();
    }

    private LocalDateTime parseFailedAt(String raw) {
        return raw != null ? LocalDateTime.parse(raw) : null;
    }
}
