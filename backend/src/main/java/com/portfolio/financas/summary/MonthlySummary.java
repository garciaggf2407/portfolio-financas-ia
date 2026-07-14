package com.portfolio.financas.summary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Resumo mensal de gastos, incluindo o texto gerado por IA (E-4). Guarda
 * apenas o total do mes e o texto da IA -- os dois valores caros de
 * recalcular -- nao a quebra por categoria, que e sempre calculada via
 * query agregada (ver docs/adr/001-modelo-dados.md).
 */
@Entity
@Table(name = "monthly_summary")
public class MonthlySummary {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Formato yyyy-MM, ex: "2026-07". Chave de negocio, UNIQUE. */
    @Column(name = "mes", nullable = false, unique = true, length = 7)
    private String mes;

    @Column(name = "total_gasto", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalGasto;

    /**
     * Texto curto (1-2 paragrafos), guardado como `text` no Postgres --
     * NAO usar @Lob aqui: no Hibernate 6, @Lob num String mapeia por
     * padrao para `oid` (large object do Postgres), incompativel com a
     * coluna `text` criada pela migration. columnDefinition forca o tipo
     * certo e evita SchemaManagementException na validacao do Hibernate.
     */
    @Column(name = "texto_gerado_por_ia", columnDefinition = "text")
    private String textoGeradoPorIa;

    @Column(name = "gerado_em")
    private LocalDateTime geradoEm;

    protected MonthlySummary() {
        // JPA
    }

    public MonthlySummary(String mes, BigDecimal totalGasto) {
        this.id = UUID.randomUUID();
        this.mes = mes;
        this.totalGasto = totalGasto;
    }

    /**
     * Persiste o resumo gerado por IA e marca o instante de geracao,
     * usado para decidir se o resumo esta cacheado e valido (E-4).
     */
    public void aplicarResumoIa(String texto, LocalDateTime geradoEm) {
        this.textoGeradoPorIa = texto;
        this.geradoEm = geradoEm;
    }

    public boolean possuiResumoIa() {
        return this.textoGeradoPorIa != null;
    }

    public UUID getId() {
        return id;
    }

    public String getMes() {
        return mes;
    }

    public BigDecimal getTotalGasto() {
        return totalGasto;
    }

    public void setTotalGasto(BigDecimal totalGasto) {
        this.totalGasto = totalGasto;
    }

    public String getTextoGeradoPorIa() {
        return textoGeradoPorIa;
    }

    public LocalDateTime getGeradoEm() {
        return geradoEm;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MonthlySummary that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
