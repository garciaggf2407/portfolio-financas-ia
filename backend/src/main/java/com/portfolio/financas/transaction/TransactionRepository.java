package com.portfolio.financas.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /**
     * Subconjunto de `hashes` que ja existe em transaction.hash_deduplicacao.
     * Usado por TransactionImportService para deduplicar um import contra
     * o que ja foi persistido (T-2.1.2).
     */
    @Query("SELECT t.hashDeduplicacao FROM Transaction t WHERE t.hashDeduplicacao IN :hashes")
    Set<String> findExistingHashes(@Param("hashes") Collection<String> hashes);
}
