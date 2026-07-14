package com.portfolio.financas.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /**
     * Listagem paginada com filtros opcionais de mes e status de
     * categorizacao, ordenada por data decrescente (GET /transactions,
     * contrato definido em E-1/T-1.2.3, implementado ao verificar E-4 --
     * gap pre-existente em E-2/E-3: o frontend (T-3.2.1) ja chamava este
     * endpoint, mas ele nunca tinha sido implementado no backend). JPQL
     * (nao query nativa) para o Spring Data derivar a count query
     * automaticamente para Page. LEFT JOIN FETCH em categoria (associacao
     * to-one, nullable) evita LazyInitializationException ao montar
     * TransactionResponse -- controller acessa a categoria fora da
     * transacao do repositorio (spring.jpa.open-in-view=false).
     */
    @Query("""
            SELECT t FROM Transaction t
            LEFT JOIN FETCH t.categoria
            WHERE (:yearMonth IS NULL OR FUNCTION('to_char', t.data, 'YYYY-MM') = :yearMonth)
            AND (:status IS NULL OR t.statusCategorizacao = :status)
            ORDER BY t.data DESC
            """)
    Page<Transaction> findAllFiltered(@Param("yearMonth") String yearMonth,
                                       @Param("status") CategorizationStatus status,
                                       Pageable pageable);

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
     * Decisao CP-2 (confirmada pelo operador na revisao humana):
     * transacoes sem categoria (categoria_id NULL) SAO incluidas via LEFT
     * JOIN -- aparecem como uma linha com `id`/`nome`/`tipo`/`criadoEm`
     * nulos, que CategorySummaryItemResponse#from traduz para
     * `categoria: null` no JSON ("Sem categoria" e responsabilidade do
     * frontend renderizar). O total usa ABS(SUM(valor)) para sempre
     * retornar magnitude positiva de gasto, independente do sinal
     * contabil bruto de `valor` (negativo = despesa). Nota: GET
     * /summary/history (sumByMonthBetween) NAO recebeu o mesmo tratamento
     * de ABS de proposito -- representa saldo liquido do mes (receita -
     * despesa), onde o sinal e informacao relevante, diferente do total
     * por categoria que e sempre gasto.
     */
    @Query(value = """
            SELECT c.id AS id, c.nome AS nome, c.tipo AS tipo, c.criado_em AS criadoEm, ABS(SUM(t.valor)) AS total
            FROM transaction t
            LEFT JOIN category c ON c.id = t.categoria_id
            WHERE to_char(t.data, 'YYYY-MM') = :yearMonth
            GROUP BY c.id, c.nome, c.tipo, c.criado_em
            ORDER BY total DESC
            """, nativeQuery = true)
    List<CategoryTotalProjection> sumByCategoryForMonth(@Param("yearMonth") String yearMonth);

    /**
     * Total de `valor` (mesma convencao de sinal do metodo acima) por mes,
     * para os meses cujo yyyy-MM esteja no intervalo [startYearMonth,
     * endYearMonth] (ambos inclusive). Meses sem nenhuma transacao
     * simplesmente nao aparecem no resultado -- o preenchimento com zero e
     * responsabilidade de SummaryHistoryService (T-2.3.2), pois a
     * agregacao no banco nao tem como "inventar" uma linha para um mes
     * sem dados.
     */
    @Query(value = """
            SELECT to_char(t.data, 'YYYY-MM') AS yearMonth, SUM(t.valor) AS total
            FROM transaction t
            WHERE to_char(t.data, 'YYYY-MM') BETWEEN :startYearMonth AND :endYearMonth
            GROUP BY to_char(t.data, 'YYYY-MM')
            """, nativeQuery = true)
    List<MonthlyTotalProjection> sumByMonthBetween(
            @Param("startYearMonth") String startYearMonth,
            @Param("endYearMonth") String endYearMonth);
}
