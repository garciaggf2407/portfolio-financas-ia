package com.portfolio.financas.transaction;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DeduplicationHashTest {

    private static final LocalDate DATA = LocalDate.of(2026, 7, 1);

    @Test
    void mesmosValoresProduzemOMesmoHash() {
        String hash1 = DeduplicationHash.compute(DATA, "Supermercado", new BigDecimal("-150.00"));
        String hash2 = DeduplicationHash.compute(DATA, "Supermercado", new BigDecimal("-150.00"));

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void hashENormalizadoPorCaseEEspacosNaDescricao() {
        String hash1 = DeduplicationHash.compute(DATA, "Supermercado", new BigDecimal("-150.00"));
        String hash2 = DeduplicationHash.compute(DATA, "  SUPERMERCADO  ", new BigDecimal("-150.00"));

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void hashENormalizadoPorEscalaDoValor() {
        String hash1 = DeduplicationHash.compute(DATA, "Supermercado", new BigDecimal("-150.00"));
        String hash2 = DeduplicationHash.compute(DATA, "Supermercado", new BigDecimal("-150"));

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void descricoesDiferentesProduzemHashesDiferentes() {
        String hash1 = DeduplicationHash.compute(DATA, "Supermercado", new BigDecimal("-150.00"));
        String hash2 = DeduplicationHash.compute(DATA, "Farmacia", new BigDecimal("-150.00"));

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void datasDiferentesProduzemHashesDiferentes() {
        String hash1 = DeduplicationHash.compute(DATA, "Supermercado", new BigDecimal("-150.00"));
        String hash2 = DeduplicationHash.compute(DATA.plusDays(1), "Supermercado", new BigDecimal("-150.00"));

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void valoresDiferentesProduzemHashesDiferentes() {
        String hash1 = DeduplicationHash.compute(DATA, "Supermercado", new BigDecimal("-150.00"));
        String hash2 = DeduplicationHash.compute(DATA, "Supermercado", new BigDecimal("-150.01"));

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void hashEUmaStringHexadecimalSha256() {
        String hash = DeduplicationHash.compute(DATA, "Supermercado", new BigDecimal("-150.00"));

        assertThat(hash).hasSize(64).matches("[0-9a-f]+");
    }
}
