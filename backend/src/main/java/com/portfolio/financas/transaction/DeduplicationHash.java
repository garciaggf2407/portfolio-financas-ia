package com.portfolio.financas.transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Calcula o hash de deduplicacao (data + descricao + valor normalizados)
 * persistido em transaction.hash_deduplicacao (V1__init.sql, UNIQUE) e
 * usado para identificar transacoes equivalentes entre imports diferentes
 * (ver docs/adr/001-modelo-dados.md).
 */
final class DeduplicationHash {

    private DeduplicationHash() {
    }

    static String compute(LocalDate data, String descricao, BigDecimal valor) {
        String normalizedDescricao = descricao.strip().toLowerCase(Locale.ROOT);
        String normalizedValor = valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
        String raw = data + "|" + normalizedDescricao + "|" + normalizedValor;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 nao disponivel na JVM.", e);
        }
    }
}
