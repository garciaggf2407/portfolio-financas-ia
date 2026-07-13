package com.portfolio.financas.transaction;

import com.portfolio.financas.category.Category;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Uma linha do extrato bancario, importada de CSV ou criada manualmente.
 * Ver docs/adr/001-modelo-dados.md.
 */
@Entity
@Table(
        name = "transaction",
        indexes = {
                // Suporte as agregacoes por mes (GET /summary/{yearMonth},
                // GET /summary/history) e listagem ordenada por data.
                @Index(name = "idx_transaction_data", columnList = "data"),
                @Index(name = "idx_transaction_categoria_id", columnList = "categoria_id")
        }
)
public class Transaction {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "data", nullable = false)
    private LocalDate data;

    @Column(name = "descricao", nullable = false, length = 500)
    private String descricao;

    @Column(name = "valor", nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Category categoria;

    @Column(name = "origem_importacao", nullable = false, length = 100)
    private String origemImportacao;

    @Column(name = "status_categorizacao", nullable = false, length = 30)
    private CategorizationStatus statusCategorizacao;

    @Column(name = "hash_deduplicacao", nullable = false, unique = true, length = 64)
    private String hashDeduplicacao;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    protected Transaction() {
        // JPA
    }

    public Transaction(LocalDate data, String descricao, BigDecimal valor,
                        String origemImportacao, String hashDeduplicacao) {
        this.id = UUID.randomUUID();
        this.data = data;
        this.descricao = descricao;
        this.valor = valor;
        this.origemImportacao = origemImportacao;
        this.hashDeduplicacao = hashDeduplicacao;
        this.statusCategorizacao = CategorizationStatus.SEM_CATEGORIA;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.criadoEm = now;
        this.atualizadoEm = now;
    }

    @PreUpdate
    void onUpdate() {
        this.atualizadoEm = LocalDateTime.now();
    }

    /**
     * Aplica categorizacao manual: sempre sobrescreve o estado atual, pois
     * a intervencao humana tem precedencia sobre a IA (ver ADR-001).
     */
    public void categorizarManualmente(Category categoria) {
        this.categoria = categoria;
        this.statusCategorizacao = CategorizationStatus.CATEGORIZADA_MANUAL;
    }

    /**
     * Aplica categorizacao automatica via IA. Nao deve ser chamada se a
     * transacao ja estiver CATEGORIZADA_MANUAL (regra validada na camada de
     * servico, T-4.1.2).
     */
    public void categorizarViaIa(Category categoria) {
        this.categoria = categoria;
        this.statusCategorizacao = CategorizationStatus.CATEGORIZADA_IA;
    }

    public boolean isCategorizadaManualmente() {
        return this.statusCategorizacao == CategorizationStatus.CATEGORIZADA_MANUAL;
    }

    public UUID getId() {
        return id;
    }

    public LocalDate getData() {
        return data;
    }

    public String getDescricao() {
        return descricao;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public Category getCategoria() {
        return categoria;
    }

    public String getOrigemImportacao() {
        return origemImportacao;
    }

    public CategorizationStatus getStatusCategorizacao() {
        return statusCategorizacao;
    }

    public String getHashDeduplicacao() {
        return hashDeduplicacao;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Transaction that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
