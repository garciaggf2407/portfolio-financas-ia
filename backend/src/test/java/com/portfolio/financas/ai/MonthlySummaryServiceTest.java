package com.portfolio.financas.ai;

import com.portfolio.financas.summary.MonthlySummary;
import com.portfolio.financas.summary.MonthlySummaryRepository;
import com.portfolio.financas.transaction.CategoryTotalProjection;
import com.portfolio.financas.transaction.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonthlySummaryServiceTest {

    @Mock
    private GroqClient groqClient;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private MonthlySummaryRepository monthlySummaryRepository;

    private MonthlySummaryService service() {
        return new MonthlySummaryService(groqClient, transactionRepository, monthlySummaryRepository);
    }

    @Test
    void mesSemTransacoesRetornaVazio() {
        when(monthlySummaryRepository.findByMes("2026-07")).thenReturn(Optional.empty());
        when(transactionRepository.sumByCategoryForMonth("2026-07")).thenReturn(List.of());

        Optional<MonthlySummary> result = service().getOrGenerate("2026-07");

        assertThat(result).isEmpty();
        verify(groqClient, never()).chat(anyString(), anyString(), anyDouble(), anyInt());
    }

    @Test
    void resumoJaCacheadoNaoChamaIaNovamente() {
        MonthlySummary cached = new MonthlySummary("2026-07", new BigDecimal("500.00"));
        cached.aplicarResumoIa("Resumo ja existente.", LocalDateTime.now());
        when(monthlySummaryRepository.findByMes("2026-07")).thenReturn(Optional.of(cached));

        Optional<MonthlySummary> result = service().getOrGenerate("2026-07");

        assertThat(result).contains(cached);
        verify(transactionRepository, never()).sumByCategoryForMonth(anyString());
        verify(groqClient, never()).chat(anyString(), anyString(), anyDouble(), anyInt());
    }

    @Test
    void primeiroGetDoMesGeraEPersisteResumo() {
        CategoryTotalProjection alimentacao = projection("Alimentacao", new BigDecimal("300.00"));
        when(monthlySummaryRepository.findByMes("2026-07")).thenReturn(Optional.empty());
        when(transactionRepository.sumByCategoryForMonth("2026-07")).thenReturn(List.of(alimentacao));
        when(transactionRepository.sumByCategoryForMonth("2026-06")).thenReturn(List.of());
        when(groqClient.chat(anyString(), anyString(), anyDouble(), anyInt()))
                .thenReturn("Voce gastou R$ 300 em julho, principalmente com Alimentacao.");

        Optional<MonthlySummary> result = service().getOrGenerate("2026-07");

        assertThat(result).isPresent();
        assertThat(result.get().getTextoGeradoPorIa()).contains("300");
        ArgumentCaptor<MonthlySummary> captor = ArgumentCaptor.forClass(MonthlySummary.class);
        verify(monthlySummaryRepository).save(captor.capture());
        assertThat(captor.getValue().getTotalGasto()).isEqualByComparingTo("300.00");
    }

    @Test
    void falhaNaGroqApiLancaSummaryGenerationException() {
        CategoryTotalProjection alimentacao = projection("Alimentacao", new BigDecimal("300.00"));
        when(monthlySummaryRepository.findByMes("2026-07")).thenReturn(Optional.empty());
        when(transactionRepository.sumByCategoryForMonth("2026-07")).thenReturn(List.of(alimentacao));
        when(transactionRepository.sumByCategoryForMonth("2026-06")).thenReturn(List.of());
        when(groqClient.chat(anyString(), anyString(), anyDouble(), anyInt()))
                .thenThrow(new GroqApiException("Timeout ao chamar a Groq API."));

        assertThatThrownBy(() -> service().getOrGenerate("2026-07"))
                .isInstanceOf(SummaryGenerationException.class);
        verify(monthlySummaryRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void receitaNaoEntraNoTotalGastoNemNaQuebraPorCategoria() {
        CategoryTotalProjection alimentacao = projection("Alimentacao", new BigDecimal("300.00"), "DESPESA");
        CategoryTotalProjection salario = projection("Salario", new BigDecimal("5000.00"), "RECEITA");
        when(monthlySummaryRepository.findByMes("2026-07")).thenReturn(Optional.empty());
        when(transactionRepository.sumByCategoryForMonth("2026-07")).thenReturn(List.of(alimentacao, salario));
        when(transactionRepository.sumByCategoryForMonth("2026-06")).thenReturn(List.of());
        when(groqClient.chat(anyString(), anyString(), anyDouble(), anyInt())).thenReturn("Resumo.");

        service().getOrGenerate("2026-07");

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(groqClient).chat(anyString(), userPromptCaptor.capture(), anyDouble(), anyInt());
        assertThat(userPromptCaptor.getValue())
                .contains("Total gasto no mes: R$ 300.00")
                .doesNotContain("Salario");

        ArgumentCaptor<MonthlySummary> savedCaptor = ArgumentCaptor.forClass(MonthlySummary.class);
        verify(monthlySummaryRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getTotalGasto()).isEqualByComparingTo("300.00");
    }

    @Test
    void corridaEntreDuasPrimeirasRequisicoesDevolveOResumoDaVencedora() {
        CategoryTotalProjection alimentacao = projection("Alimentacao", new BigDecimal("300.00"), "DESPESA");
        MonthlySummary jaPersistidoPelaOutraRequisicao = new MonthlySummary("2026-07", new BigDecimal("300.00"));
        jaPersistidoPelaOutraRequisicao.aplicarResumoIa("Resumo da vencedora da corrida.", LocalDateTime.now());

        when(monthlySummaryRepository.findByMes("2026-07"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(jaPersistidoPelaOutraRequisicao));
        when(transactionRepository.sumByCategoryForMonth("2026-07")).thenReturn(List.of(alimentacao));
        when(transactionRepository.sumByCategoryForMonth("2026-06")).thenReturn(List.of());
        when(groqClient.chat(anyString(), anyString(), anyDouble(), anyInt())).thenReturn("Meu resumo (perdedor).");
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("unique constraint violation"))
                .when(monthlySummaryRepository).save(any());

        Optional<MonthlySummary> result = service().getOrGenerate("2026-07");

        assertThat(result).contains(jaPersistidoPelaOutraRequisicao);
    }

    private CategoryTotalProjection projection(String nome, BigDecimal total) {
        return projection(nome, total, "DESPESA");
    }

    private CategoryTotalProjection projection(String nome, BigDecimal total, String tipo) {
        return new CategoryTotalProjection() {
            @Override
            public UUID getId() {
                return UUID.randomUUID();
            }

            @Override
            public String getNome() {
                return nome;
            }

            @Override
            public String getTipo() {
                return tipo;
            }

            @Override
            public LocalDateTime getCriadoEm() {
                return LocalDateTime.now();
            }

            @Override
            public BigDecimal getTotal() {
                return total;
            }
        };
    }
}
