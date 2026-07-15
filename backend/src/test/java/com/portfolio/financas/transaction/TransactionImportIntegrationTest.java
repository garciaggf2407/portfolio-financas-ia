package com.portfolio.financas.transaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.financas.support.AbstractIntegrationTest;
import com.portfolio.financas.transaction.dto.ImportResult;
import com.portfolio.financas.transaction.dto.TransactionPageResponse;
import com.portfolio.financas.transaction.dto.TransactionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Import de CSV -> persistencia -> GET /transactions contra Postgres real
 * (Testcontainers), nao mockado. Cobre o gap fechado em 588571f: GET
 * /transactions nunca tinha sido exercitado ponta a ponta antes de E-4.
 */
class TransactionImportIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void importaCsvEAsTransacoesFicamVisiveisViaGetTransactions() throws Exception {
        String csv = """
                data,descricao,valor
                01/07/2026,Supermercado,-150.00
                02/07/2026,Salario,3000.00
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "extrato-julho.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        String importJson = mockMvc.perform(multipart("/transactions/import").file(file))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        ImportResult importResult = objectMapper.readValue(importJson, ImportResult.class);
        assertThat(importResult.importadas()).isEqualTo(2);
        assertThat(importResult.ignoradasDuplicadas()).isEqualTo(0);
        assertThat(importResult.invalidas()).isEmpty();

        // size=200 (o maximo aceito por GET /transactions) e assertions por
        // conteudo, nao por totalElements exato: os demais metodos desta
        // classe (e CategorizationRetryDlqIntegrationTest) tambem persistem
        // transacoes de julho/2026 no MESMO Postgres (containers
        // compartilhados de proposito, ver AbstractIntegrationTest), e a
        // ordem de execucao dos testes nao e garantida.
        String listJson = mockMvc.perform(get("/transactions").param("yearMonth", "2026-07").param("size", "200"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        TransactionPageResponse page = objectMapper.readValue(listJson, TransactionPageResponse.class);

        // Nao afirma statusCategorizacao aqui: o import publica uma mensagem
        // assincrona de categorizacao no RabbitMQ compartilhado (mesmo
        // broker que CategorizationRetryDlqIntegrationTest usa), entao o
        // resultado da categorizacao e responsabilidade daquele teste, nao
        // deste -- este so verifica o contrato de import + listagem.
        assertThat(page.content()).extracting(TransactionResponse::descricao)
                .contains("Supermercado", "Salario");
    }

    @Test
    void listaTransacoesSemFiltroDeMesOuStatusNaoQuebra() throws Exception {
        // Caso nunca coberto antes: GET /transactions SEM yearMonth (nem
        // status) e o comportamento DEFAULT de TransactionsPage.tsx (nao
        // manda esses parametros) -- quebrava em producao (500, "could not
        // determine data type of parameter") porque :yearMonth chegava NULL
        // sem tipo explicito numa comparacao contra FUNCTION('to_char',...).
        // Os demais testes desta classe sempre passam yearMonth explicito,
        // entao nunca exercitaram este caminho.
        String csv = """
                data,descricao,valor
                05/07/2026,Padaria,-12.50
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "extrato-padaria.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/transactions/import").file(file)).andExpect(status().isOk());

        mockMvc.perform(get("/transactions"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/transactions").param("status", "sem_categoria"))
                .andExpect(status().isOk());
    }

    @Test
    void reimportarOMesmoArquivoEDeduplicadoContraOHashJaPersistidoNoBanco() throws Exception {
        String csv = """
                data,descricao,valor
                03/07/2026,Aluguel,-1200.00
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "extrato-aluguel.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/transactions/import").file(file)).andExpect(status().isOk());

        String secondImportJson = mockMvc.perform(multipart("/transactions/import").file(file))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        ImportResult secondResult = objectMapper.readValue(secondImportJson, ImportResult.class);

        assertThat(secondResult.importadas()).isEqualTo(0);
        assertThat(secondResult.ignoradasDuplicadas()).isEqualTo(1);
    }

    @Test
    void importarArquivoVazioRetorna400ComErroDeContrato() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "vazio.csv", "text/csv", new byte[0]);

        mockMvc.perform(multipart("/transactions/import").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void csvComLinhaInvalidaReportaNoResultadoSemQuebrarOImportDasLinhasValidas() throws Exception {
        String csv = """
                data,descricao,valor
                04/07/2026,Farmacia,-80.00
                data-invalida,Linha Quebrada,-10.00
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "extrato-misto.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        String importJson = mockMvc.perform(multipart("/transactions/import").file(file))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        ImportResult result = objectMapper.readValue(importJson, ImportResult.class);

        assertThat(result.importadas()).isEqualTo(1);
        assertThat(result.invalidas()).hasSize(1);
        assertThat(result.invalidas().get(0).line()).isEqualTo(3);
    }
}
