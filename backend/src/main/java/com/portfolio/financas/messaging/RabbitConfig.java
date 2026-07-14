package com.portfolio.financas.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia RabbitMQ da categorizacao assincrona (T-4.3.1): exchange, fila
 * e binding, declarados via configuracao -- nao dependem de criacao manual
 * no RabbitMQ. Mensagens serializadas como JSON (Jackson2JsonMessageConverter)
 * para ficarem inspecionaveis na management UI (localhost:15672).
 */
@Configuration
public class RabbitConfig {

    public static final String CATEGORIZATION_EXCHANGE = "financas.categorization.exchange";
    public static final String CATEGORIZATION_QUEUE = "transaction.categorization";
    public static final String CATEGORIZATION_ROUTING_KEY = "categorization";

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
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        return factory;
    }
}
