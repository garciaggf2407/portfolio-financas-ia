package com.portfolio.financas.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
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

    /**
     * Total gasto por categoria no mes informado (yyyy-MM), maior para o
     * menor. Agregacao feita no banco (SUM + GROUP BY), nao em memoria --
     * ver T-2.3.1.
     *
     * DECISAO PARA REVISAO: transacoes sem categoria (categoria_id NULL)
     * sao excluidas deste somatorio via INNER JOIN, pois o schema
     * CategorySummaryItem em docs/openapi.yaml exige `categoria`
     * nao-nula. O valor somado tambem preserva o sinal de `valor`
     * (negativo = despesa, positivo = receita, ver Transaction.valor) em
     * vez de usar valor absoluto -- ou seja, uma categoria de despesa
     * hoje aparece com total negativo. Ambas as escolhas devem ser
     * confirmadas na revisao humana (CP-2): pode fazer mais sentido expor
     * um pseudo-item "sem categoria" e/ou normalizar o total para
     * positivo.
     */
    @Query(value = """
            SELECT c.id AS id, c.nome AS nome, c.tipo AS tipo, c.criado_em AS criadoEm, SUM(t.valor) AS total
            FROM transaction t
            JOIN category c ON c.id = t.categoria_id
            WHERE to_char(t.data, 'YYYY-MM') = :yearMonth
            GROUP BY c.id, c.nome, c.tipo, c.criado_em
            ORDER BY total DESC
            """, nativeQuery = true)
    List<CategoryTotalProjection> sumByCategoryForMonth(@Param("yearMonth") String yearMonth);
}
