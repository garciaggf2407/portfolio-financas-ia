package com.portfolio.financas.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.financas.ai.CategorizationApiException;
import com.portfolio.financas.ai.TransactionCategorizationService;
import com.portfolio.financas.category.Category;
import com.portfolio.financas.category.CategoryRepository;
import com.portfolio.financas.category.CategoryType;
import com.portfolio.financas.messaging.dto.CategorizationFailureResponse;
import com.portfolio.financas.support.AbstractIntegrationTest;
import com.portfolio.financas.transaction.CategorizationStatus;
import com.portfolio.financas.transaction.Transaction;
import com.portfolio.financas.transaction.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercita a topologia real de retry + dead-letter queue de RabbitConfig
 * (T-4.3.2) contra um broker real (Testcontainers), publicando mensagens
 * diretamente e deixando o adviceChain (retry com backoff + republish na
 * DLQ) rodar de ponta a ponta -- nao apenas verificando (como
 * CategorizationConsumerTest) que a excecao propaga do consumer. Apenas
 * TransactionCategorizationService e mockado, para tornar a falha
 * deterministica sem depender da rede/Groq API; toda a mensageria
 * (producer, consumer, retry interceptor, DLX/DLQ, endpoint admin) e real.
 *
 * Exchange/fila/DLQ isolados com nomes unicos (sufixo "retrydlqtest") via
 * categorization.messaging.* -- esta classe faz asserções sensiveis a
 * timing sobre o resultado exato de cada mensagem, entao precisa ser a
 * UNICA consumidora da sua fila. Sem isso, qualquer outro contexto Spring
 * de teste ainda vivo no cache do Spring (todos compartilham o mesmo
 * broker Testcontainers, singleton container pattern) registra seu proprio
 * @RabbitListener na mesma fila fisica e compete pelas mensagens --
 * "competing consumers" ja causou flakiness real em CI (visto em
 * 41452bc, e de novo nesta mesma classe antes deste fix).
 */
@TestPropertySource(properties = {
        "categorization.retry.max-attempts=2",
        "categorization.retry.initial-interval-ms=50",
        "categorization.retry.multiplier=1.0",
        "categorization.messaging.exchange=financas.categorization.exchange.retrydlqtest",
        "categorization.messaging.queue=transaction.categorization.retrydlqtest",
        "categorization.messaging.routing-key=categorization.retrydlqtest",
        "categorization.messaging.dlx=financas.categorization.dlx.retrydlqtest",
        "categorization.messaging.dlq=transaction.categorization.dlq.retrydlqtest",
        "categorization.messaging.dlq-routing-key=categorization.dlq.retrydlqtest"
})
class CategorizationRetryDlqIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CategorizationProducer categorizationProducer;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TransactionCategorizationService categorizationService;

    private Transaction persistTransaction(String descricao) {
        Transaction transaction = new Transaction(
                LocalDate.of(2026, 7, 5), descricao, new BigDecimal("-42.00"),
                "teste-retry-dlq", UUID.randomUUID().toString());
        return transactionRepository.saveAndFlush(transaction);
    }

    @Test
    void mensagemQueFalhaRepetidamenteEsgotaAsTentativasECaiNaDeadLetterQueue() throws Exception {
        when(categorizationService.categorize(anyString()))
                .thenThrow(new CategorizationApiException("Groq API indisponivel (simulado)"));
        Transaction transaction = persistTransaction("Compra que sempre falha na categorizacao");

        categorizationProducer.publishAll(List.of(transaction.getId()));

        String failuresJson = await()
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(200))
                .until(this::fetchFailures, json -> json.contains(transaction.getId().toString()));

        List<CategorizationFailureResponse> failures = objectMapper.readValue(
                failuresJson, new TypeReference<List<CategorizationFailureResponse>>() {
                });
        CategorizationFailureResponse failure = failures.stream()
                .filter(f -> f.transactionId().equals(transaction.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(failure.descricao()).isEqualTo("Compra que sempre falha na categorizacao");
        assertThat(failure.tentativas()).isEqualTo(2);
        assertThat(failure.ultimoErro()).contains("Groq API indisponivel");

        Transaction reloaded = transactionRepository.findById(transaction.getId()).orElseThrow();
        assertThat(reloaded.getStatusCategorizacao()).isEqualTo(CategorizationStatus.SEM_CATEGORIA);
    }

    @Test
    void mensagemComSucessoNaPrimeiraTentativaNuncaChegaNaDeadLetterQueue() throws Exception {
        Category categoria = categoryRepository.save(
                new Category("CategoriaTesteRetryDlq-" + UUID.randomUUID(), CategoryType.DESPESA));
        when(categorizationService.categorize(anyString())).thenReturn(categoria);
        Transaction transaction = persistTransaction("Compra categorizada de primeira");

        categorizationProducer.publishAll(List.of(transaction.getId()));

        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
            Transaction reloaded = transactionRepository.findById(transaction.getId()).orElseThrow();
            assertThat(reloaded.getStatusCategorizacao()).isEqualTo(CategorizationStatus.CATEGORIZADA_IA);
        });

        assertThat(fetchFailures()).doesNotContain(transaction.getId().toString());
    }

    private String fetchFailures() throws Exception {
        return mockMvc.perform(get("/admin/categorization/failures"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
