package com.portfolio.financas.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Topologia RabbitMQ da categorizacao assincrona (T-4.3.1) e a politica de
 * retry + dead-letter (T-4.3.2).
 *
 * Retry e client-side (Spring Retry, via adviceChain do listener container)
 * em vez de requeue-and-recount pelo proprio broker: da controle explicito
 * sobre o backoff exponencial e sobre o exato momento de desistir e
 * publicar na DLQ, sem depender de inspecionar o header x-death do
 * RabbitMQ. O consumer (CategorizationConsumer) so precisa deixar a
 * excecao propagar -- todo o retry/recovery acontece fora dele.
 *
 * Nomes de exchange/fila/DLQ sao configuraveis (categorization.messaging.*,
 * default = nomes historicos) em vez de constantes fixas: contextos Spring
 * de teste diferentes (um por classe de integracao) compartilham o mesmo
 * broker Testcontainers (singleton container pattern), e com nomes fixos
 * cada contexto registra seu proprio @RabbitListener na MESMA fila fisica
 * -- "competing consumers" entre classes de teste (ja visto e so
 * parcialmente contornado em 41452bc). CategorizationRetryDlqIntegrationTest
 * sobrescreve essas properties com nomes unicos pra eliminar a raiz do
 * problema, nao so o sintoma.
 */
@Configuration
public class RabbitConfig {

    static final String HEADER_ATTEMPTS = "x-tentativas";
    static final String HEADER_FAILED_AT = "x-falhou-em";

    private final String exchangeName;
    private final String queueName;
    private final String routingKey;
    private final String dlxName;
    private final String dlqName;
    private final String dlqRoutingKey;

    public RabbitConfig(
            @Value("${categorization.messaging.exchange:financas.categorization.exchange}") String exchangeName,
            @Value("${categorization.messaging.queue:transaction.categorization}") String queueName,
            @Value("${categorization.messaging.routing-key:categorization}") String routingKey,
            @Value("${categorization.messaging.dlx:financas.categorization.dlx}") String dlxName,
            @Value("${categorization.messaging.dlq:transaction.categorization.dlq}") String dlqName,
            @Value("${categorization.messaging.dlq-routing-key:categorization.dlq}") String dlqRoutingKey) {
        this.exchangeName = exchangeName;
        this.queueName = queueName;
        this.routingKey = routingKey;
        this.dlxName = dlxName;
        this.dlqName = dlqName;
        this.dlqRoutingKey = dlqRoutingKey;
    }

    @Bean
    DirectExchange categorizationExchange() {
        return new DirectExchange(exchangeName);
    }

    @Bean
    Queue categorizationQueue() {
        return QueueBuilder.durable(queueName).build();
    }

    @Bean
    Binding categorizationBinding(Queue categorizationQueue, DirectExchange categorizationExchange) {
        return BindingBuilder.bind(categorizationQueue).to(categorizationExchange).with(routingKey);
    }

    @Bean
    DirectExchange categorizationDlx() {
        return new DirectExchange(dlxName);
    }

    @Bean
    Queue categorizationDlq() {
        return QueueBuilder.durable(dlqName).build();
    }

    @Bean
    Binding categorizationDlqBinding(Queue categorizationDlq, DirectExchange categorizationDlx) {
        return BindingBuilder.bind(categorizationDlq).to(categorizationDlx).with(dlqRoutingKey);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }

    /**
     * Acionado quando o retry interceptor esgota as tentativas: republica a
     * mensagem original na DLQ, preservando o payload e anexando headers
     * com o numero de tentativas e o momento da falha -- lidos por
     * CategorizationFailureController para montar CategorizationFailure.
     */
    @Bean
    RepublishMessageRecoverer categorizationRecoverer(
            RabbitTemplate rabbitTemplate,
            @Value("${categorization.retry.max-attempts:3}") int maxAttempts) {
        return new RepublishMessageRecoverer(rabbitTemplate, dlxName, dlqRoutingKey) {
            @Override
            protected Map<? extends String, ?> additionalHeaders(Message message, Throwable cause) {
                Map<String, Object> headers = new HashMap<>();
                headers.put(HEADER_ATTEMPTS, maxAttempts);
                // LocalDateTime.toString() (nao Instant.toString(), que
                // termina em 'Z' e quebra o LocalDateTime.parse() feito
                // por CategorizationFailureService#parseFailedAt).
                headers.put(HEADER_FAILED_AT, LocalDateTime.now().toString());
                return headers;
            }
        };
    }

    @Bean
    RetryOperationsInterceptor categorizationRetryInterceptor(
            RepublishMessageRecoverer categorizationRecoverer,
            @Value("${categorization.retry.max-attempts:3}") int maxAttempts,
            @Value("${categorization.retry.initial-interval-ms:1000}") long initialIntervalMs,
            @Value("${categorization.retry.multiplier:2.0}") double multiplier) {

        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(new SimpleRetryPolicy(maxAttempts));

        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(initialIntervalMs);
        backOffPolicy.setMultiplier(multiplier);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return RetryInterceptorBuilder.stateless()
                .retryOperations(retryTemplate)
                .recoverer(categorizationRecoverer)
                .build();
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter,
            RetryOperationsInterceptor categorizationRetryInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setAdviceChain(categorizationRetryInterceptor);
        return factory;
    }
}
