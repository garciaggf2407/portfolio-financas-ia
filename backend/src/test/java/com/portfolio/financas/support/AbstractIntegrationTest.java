package com.portfolio.financas.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

/**
 * Base para testes de integracao (T-5.1.2/T-5.1.3): sobe Postgres e
 * RabbitMQ reais via Testcontainers em vez de depender do
 * docker-compose.yml de desenvolvimento -- hermetico e roda em CI sem
 * setup manual.
 *
 * Deliberadamente NAO usa @Testcontainers/@Container: essa combinacao
 * gerencia o ciclo de vida por CLASSE de teste (para o container no
 * afterAll de cada classe, mesmo campos estaticos herdados), entao com
 * varias subclasses o container era parado e recriado em portas
 * diferentes a cada classe, enquanto o ApplicationContext do Spring
 * (cacheado e reaproveitado entre classes com a mesma config) continuava
 * apontando para a porta do container original, ja morto -- causava
 * "Connection is not available"/"Connection refused" em testes que
 * rodavam depois do primeiro. Iniciar os containers manualmente em bloco
 * estatico (o "singleton container pattern" documentado pelo proprio
 * Testcontainers) evita isso: inicia uma vez por JVM e nunca para
 * explicitamente -- o Ryuk (reaper interno do Testcontainers) derruba os
 * containers no fim da JVM.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @ServiceConnection
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    static {
        POSTGRES.start();
        RABBITMQ.start();
    }

    @Autowired
    protected MockMvc mockMvc;
}
