package com.portfolio.financas.ai;

import com.portfolio.financas.category.Category;
import com.portfolio.financas.category.CategoryType;
import com.portfolio.financas.common.ResourceNotFoundException;
import com.portfolio.financas.transaction.Transaction;
import com.portfolio.financas.transaction.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutoCategorizationApplierTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionCategorizationService categorizationService;

    @Test
    void transacaoSemCategoriaRecebeCategoriaViaIaEStatusCorreto() {
        Transaction transaction = new Transaction(
                LocalDate.of(2026, 7, 1), "Supermercado", new BigDecimal("-150.00"), "extrato.csv", "hash-1");
        Category categoria = new Category("Alimentacao", CategoryType.DESPESA);

        when(transactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));
        when(categorizationService.categorize("Supermercado")).thenReturn(categoria);

        AutoCategorizationApplier applier = new AutoCategorizationApplier(transactionRepository, categorizationService);
        applier.aplicar(transaction.getId());

        assertThat(transaction.getCategoria()).isEqualTo(categoria);
        assertThat(transaction.getStatusCategorizacao().name()).isEqualTo("CATEGORIZADA_IA");
        verify(transactionRepository).save(transaction);
    }

    @Test
    void transacaoJaCategorizadaManualmenteNaoESobrescrita() {
        Transaction transaction = new Transaction(
                LocalDate.of(2026, 7, 1), "Supermercado", new BigDecimal("-150.00"), "extrato.csv", "hash-2");
        Category categoriaManual = new Category("Outros", CategoryType.DESPESA);
        transaction.categorizarManualmente(categoriaManual);

        when(transactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));

        AutoCategorizationApplier applier = new AutoCategorizationApplier(transactionRepository, categorizationService);
        applier.aplicar(transaction.getId());

        assertThat(transaction.getCategoria()).isEqualTo(categoriaManual);
        assertThat(transaction.getStatusCategorizacao().name()).isEqualTo("CATEGORIZADA_MANUAL");
        verify(categorizationService, never()).categorize(anyString());
        verify(transactionRepository, never()).save(transaction);
    }

    @Test
    void transacaoInexistenteLancaResourceNotFoundException() {
        UUID id = UUID.randomUUID();
        when(transactionRepository.findById(id)).thenReturn(Optional.empty());

        AutoCategorizationApplier applier = new AutoCategorizationApplier(transactionRepository, categorizationService);

        assertThatThrownBy(() -> applier.aplicar(id)).isInstanceOf(ResourceNotFoundException.class);
    }
}
