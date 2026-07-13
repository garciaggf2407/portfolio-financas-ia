package com.portfolio.financas.transaction;

/**
 * Ciclo de vida de categorizacao de uma transacao. Ver
 * docs/adr/001-modelo-dados.md, secao "Ciclo de vida de categorizacao".
 *
 * <pre>
 *         sem_categoria
 *          /          \
 *   PATCH manual    consumer de IA (E-4)
 *         v               v
 * categorizada_manual  categorizada_ia
 * </pre>
 *
 * categorizada_manual nunca e sobrescrita pela categorizacao automatica
 * (regra aplicada em AutoCategorizationApplier, E-4).
 */
public enum CategorizationStatus {
    SEM_CATEGORIA,
    CATEGORIZADA_MANUAL,
    CATEGORIZADA_IA
}
