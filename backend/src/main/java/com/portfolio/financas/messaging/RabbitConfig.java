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
 */
@Configuration
public class RabbitConfig {

    public static final String CATEGORIZATION_EXCHANGE = "financas.categorization.exchange";
    public static final String CATEGORIZATION_QUEUE = "transaction.categorization";
    public static final String CATEGORIZATION_ROUTING_KEY = "categorization";

    public static final String CATEGORIZATION_DLX = "financas.categorization.dlx";
    public static final String CATEGORIZATION_DLQ = "transaction.categorization.dlq";
    public static final String CATEGORIZATION_DLQ_ROUTING_KEY = "categorization.dlq";

    static final String HEADER_ATTEMPTS = "x-tentativas";
    static final String HEADER_FAILED_AT = "x-falhou-em";

    @Bean
    DirectExchange categorizationExchange() {
        return new DirectExchange(CATEGORIZATION_EXCHANGE);
    }

    @Bean
    Queue categorizationQueue() {
        return QueueBuilder.durable(CATEGORIZATION_QUEUE).build();
    }

    @Bean
    Binding categorizationBinding(Queue categorizationQueue, DirectExchange categorizationExchange) {
        return BindingBuilder.bind(categorizationQueue).to(categorizationExchange).with(CATEGORIZATION_ROUTING_KEY);
    }

    @Bean
    DirectExchange categorizationDlx() {
        return new DirectExchange(CATEGORIZATION_DLX);
    }

    @Bean
    Queue categorizationDlq() {
        return QueueBuilder.durable(CATEGORIZATION_DLQ).build();
    }

    @Bean
    Binding categorizationDlqBinding(Queue categorizationDlq, DirectExchange categorizationDlx) {
        return BindingBuilder.bind(categorizationDlq).to(categorizationDlx).with(CATEGORIZATION_DLQ_ROUTING_KEY);
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
        return new RepublishMessageRecoverer(rabbitTemplate, CATEGORIZATION_DLX, CATEGORIZATION_DLQ_ROUTING_KEY) {
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
