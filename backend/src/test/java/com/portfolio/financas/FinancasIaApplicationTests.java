package com.portfolio.financas;

import com.portfolio.financas.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Smoke test: o contexto Spring completo sobe sem erro. Estende
 * AbstractIntegrationTest (Postgres + RabbitMQ via Testcontainers) em vez
 * de depender do docker-compose.yml de desenvolvimento em localhost:5433/
 * 5672 -- essa dependencia implicita so falhava em CI (sem docker-compose
 * rodando), nunca localmente, ate ser descoberta ao configurar T-5.2.1.
 */
class FinancasIaApplicationTests extends AbstractIntegrationTest {

	@Test
	void contextLoads() {
	}

}
