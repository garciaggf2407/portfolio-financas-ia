package com.portfolio.financas.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base para testes de integracao (T-5.1.2/T-5.1.3): sobe Postgres e
 * RabbitMQ reais via Testcontainers em vez de depender do
 * docker-compose.yml de desenvolvimento -- hermetico e roda em CI sem
 * setup manual. Os containers sao campos estaticos declarados aqui (nao
 * em cada subclasse) de proposito: e o "singleton container pattern" do
 * Testcontainers -- inicia uma vez por JVM e e reaproveitado por todas as
 * subclasses, em vez de subir um par de containers por classe de teste.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @Autowired
    protected MockMvc mockMvc;
}
