package com.portfolio.financas.ai;

import com.portfolio.financas.summary.MonthlySummary;
import com.portfolio.financas.summary.MonthlySummaryRepository;
import com.portfolio.financas.transaction.CategoryTotalProjection;
import com.portfolio.financas.transaction.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Gera e cacheia o resumo mensal em linguagem natural (T-4.2.1). Chamado
 * sincronamente por GET /summary/{yearMonth}/ai -- o primeiro GET de um mes
 * paga o custo da chamada a Groq API, os seguintes leem o texto persistido
 * em MonthlySummary (ver docs/openapi.yaml).
 */
@Service
public class MonthlySummaryService {

    private static final DateTimeFormatter YEAR_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private static final String SYSTEM_PROMPT = """
            Voce e um assistente financeiro pessoal. Escreva um resumo curto
            (1 a 2 paragrafos, em portugues) dos gastos do mes, com tom
            direto e informativo. Sempre que houver dado do mes anterior,
            inclua uma comparacao explicita (maior, menor ou igual, com a
            diferenca aproximada). Nao invente numeros que nao foram
            fornecidos no prompt. Responda apenas com o texto do resumo, sem
            titulo e sem marcacao markdown.""";

    private final GroqClient groqClient;
    private final TransactionRepository transactionRepository;
    private final MonthlySummaryRepository monthlySummaryRepository;

    public MonthlySummaryService(GroqClient groqClient,
                                  TransactionRepository transactionRepository,
                                  MonthlySummaryRepository monthlySummaryRepository) {
        this.groqClient = groqClient;
        this.transactionRepository = transactionRepository;
        this.monthlySummaryRepository = monthlySummaryRepository;
    }

    /**
     * @param yearMonth mes no formato yyyy-MM
     * @return resumo cacheado ou recem-gerado; Optional vazio quando o mes
     *         nao tem nenhuma transacao (o controller traduz isso para HTTP
     *         202)
     * @throws SummaryGenerationException se a chamada a Groq API falhar --
     *                                    o controller traduz para HTTP 503
     */
    @Transactional
    public Optional<MonthlySummary> getOrGenerate(String yearMonth) {
        Optional<MonthlySummary> existing = monthlySummaryRepository.findByMes(yearMonth);
        if (existing.isPresent() && existing.get().possuiResumoIa()) {
            return existing;
        }

        List<CategoryTotalProjection> categoriasAtual = transactionRepository.sumByCategoryForMonth(yearMonth);
        if (categoriasAtual.isEmpty()) {
            return Optional.empty();
        }

        BigDecimal totalAtual = somaTotais(categoriasAtual);
        List<CategoryTotalProjection> categoriasAnterior =
                transactionRepository.sumByCategoryForMonth(mesAnterior(yearMonth));
        BigDecimal totalAnterior = categoriasAnterior.isEmpty() ? null : somaTotais(categoriasAnterior);

        String texto;
        try {
            texto = groqClient.chat(
                    SYSTEM_PROMPT,
                    buildUserPrompt(yearMonth, categoriasAtual, totalAtual, totalAnterior),
                    0.4,
                    400);
        } catch (GroqApiException e) {
            throw new SummaryGenerationException("Falha ao gerar resumo mensal via IA.", e);
        }

        MonthlySummary summary = existing.orElseGet(() -> new MonthlySummary(yearMonth, totalAtual));
        summary.setTotalGasto(totalAtual);
        summary.aplicarResumoIa(texto, LocalDateTime.now());
        monthlySummaryRepository.save(summary);

        return Optional.of(summary);
    }

    private String mesAnterior(String yearMonth) {
        return YearMonth.parse(yearMonth, YEAR_MONTH_FORMAT).minusMonths(1).format(YEAR_MONTH_FORMAT);
    }

    private BigDecimal somaTotais(List<CategoryTotalProjection> categorias) {
        return categorias.stream()
                .map(CategoryTotalProjection::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String buildUserPrompt(String yearMonth, List<CategoryTotalProjection> categoriasAtual,
                                    BigDecimal totalAtual, BigDecimal totalAnterior) {
        String quebraPorCategoria = categoriasAtual.stream()
                .map(c -> "- %s: R$ %s".formatted(
                        c.getNome() != null ? c.getNome() : "Sem categoria", c.getTotal()))
                .collect(Collectors.joining("\n"));

        String linhaMesAnterior = totalAnterior != null
                ? "Total gasto no mes anterior: R$ %s".formatted(totalAnterior)
                : "Nao ha dados do mes anterior (provavelmente o primeiro mes com transacoes) -- "
                        + "nao faca comparacao numerica, apenas mencione isso.";

        return """
                Mes de referencia: %s
                Total gasto no mes: R$ %s
                %s

                Gastos por categoria no mes:
                %s""".formatted(yearMonth, totalAtual, linhaMesAnterior, quebraPorCategoria);
    }
}
