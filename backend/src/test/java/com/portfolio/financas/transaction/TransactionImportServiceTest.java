package com.portfolio.financas.transaction;

import com.portfolio.financas.transaction.dto.ImportResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionImportServiceTest {

    private static final String CSV = """
            data,descricao,valor
            01/07/2026,Supermercado,-150.00
            02/07/2026,Salario,3000.00
            """;

    @Mock
    private TransactionRepository transactionRepository;

    @Test
    void primeiroImportPersisteTodasAsLinhasValidas() {
        when(transactionRepository.findExistingHashes(any())).thenReturn(Set.of());
        TransactionImportService service = new TransactionImportService(transactionRepository);

        ImportResult result = service.importCsv(CSV, "extrato-julho.csv");

        assertThat(result.importadas()).isEqualTo(2);
        assertThat(result.ignoradasDuplicadas()).isEqualTo(0);
        assertThat(result.invalidas()).isEmpty();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Transaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactionRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    void reimportarOMesmoArquivoIgnoraTodasAsLinhasComoDuplicadas() {
        // Simula o estado do banco apos o primeiro import: os hashes das
        // duas linhas do CSV ja existem em transaction.hash_deduplicacao.
        String hashSupermercado = DeduplicationHash.compute(
                LocalDate.of(2026, 7, 1), "Supermercado", new BigDecimal("-150.00"));
        String hashSalario = DeduplicationHash.compute(
                LocalDate.of(2026, 7, 2), "Salario", new BigDecimal("3000.00"));

        when(transactionRepository.findExistingHashes(any()))
                .thenReturn(Set.of(hashSupermercado, hashSalario));
        TransactionImportService service = new TransactionImportService(transactionRepository);

        ImportResult result = service.importCsv(CSV, "extrato-julho.csv");

        assertThat(result.importadas()).isEqualTo(0);
        assertThat(result.ignoradasDuplicadas()).isEqualTo(2);
        assertThat(result.invalidas()).isEmpty();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Transaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactionRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    void linhaRepetidaDentroDoMesmoArquivoEIgnoradaComoDuplicataAposAPrimeiraOcorrencia() {
        String csvComLinhaRepetida = """
                data,descricao,valor
                01/07/2026,Supermercado,-150.00
                01/07/2026,Supermercado,-150.00
                """;
        when(transactionRepository.findExistingHashes(any())).thenReturn(Set.of());
        TransactionImportService service = new TransactionImportService(transactionRepository);

        ImportResult result = service.importCsv(csvComLinhaRepetida, "extrato-julho.csv");

        assertThat(result.importadas()).isEqualTo(1);
        assertThat(result.ignoradasDuplicadas()).isEqualTo(1);
    }

    @Test
    void findExistingHashesRecebeOsHashesDeTodasAsLinhasValidas() {
        when(transactionRepository.findExistingHashes(any())).thenReturn(Set.of());
        TransactionImportService service = new TransactionImportService(transactionRepository);

        service.importCsv(CSV, "extrato-julho.csv");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(transactionRepository).findExistingHashes(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }
}
