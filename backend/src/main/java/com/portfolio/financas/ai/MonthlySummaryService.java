package com.portfolio.financas.ai;

import com.portfolio.financas.category.CategoryType;
import com.portfolio.financas.summary.MonthlySummary;
import com.portfolio.financas.summary.MonthlySummaryRepository;
import com.portfolio.financas.transaction.CategoryTotalProjection;
import com.portfolio.financas.transaction.TransactionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

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
    public Optional<MonthlySummary> getOrGenerate(String yearMonth) {
        Optional<MonthlySummary> existing = monthlySummaryRepository.findByMes(yearMonth);
        if (existing.isPresent() && existing.get().possuiResumoIa()) {
            return existing;
        }

        List<CategoryTotalProjection> transacoesDoMes = transactionRepository.sumByCategoryForMonth(yearMonth);
        if (transacoesDoMes.isEmpty()) {
            return Optional.empty();
        }

        List<CategoryTotalProjection> despesasAtual = apenasDespesas(transacoesDoMes);
        BigDecimal totalAtual = somaTotais(despesasAtual);
        List<CategoryTotalProjection> despesasAnterior =
                apenasDespesas(transactionRepository.sumByCategoryForMonth(mesAnterior(yearMonth)));
        BigDecimal totalAnterior = despesasAnterior.isEmpty() ? null : somaTotais(despesasAnterior);

        // Chamada de rede (segundos de latencia) feita fora de qualquer
        // transacao/conexao de banco -- nao ha @Transactional neste
        // metodo de proposito, para nao reter uma conexao do pool
        // enquanto se espera a Groq API responder.
        String texto;
        try {
            texto = groqClient.chat(
                    SYSTEM_PROMPT,
                    buildUserPrompt(yearMonth, despesasAtual, totalAtual, totalAnterior),
                    0.4,
                    400);
        } catch (GroqApiException e) {
            throw new SummaryGenerationException("Falha ao gerar resumo mensal via IA.", e);
        }

        MonthlySummary summary = existing.orElseGet(() -> new MonthlySummary(yearMonth, totalAtual));
        summary.setTotalGasto(totalAtual);
        summary.aplicarResumoIa(texto, LocalDateTime.now());
        try {
            monthlySummaryRepository.save(summary);
        } catch (DataIntegrityViolationException e) {
            // Duas primeiras requisicoes para o mesmo mes em paralelo: a
            // outra venceu a corrida contra a constraint UNIQUE(mes) e ja
            // persistiu o resumo dela primeiro. Em vez de propagar erro,
            // devolve o resumo da vencedora -- o resultado e o mesmo do
            // ponto de vista do cliente (um resumo valido para o mes).
            return monthlySummaryRepository.findByMes(yearMonth);
        }

        return Optional.of(summary);
    }

    private String mesAnterior(String yearMonth) {
        return YearMonth.parse(yearMonth, YEAR_MONTH_FORMAT).minusMonths(1).format(YEAR_MONTH_FORMAT);
    }

    /**
     * Exclui categorias RECEITA -- total_gasto e a "soma de todas as
     * despesas do mes" (docs/adr/001-modelo-dados.md), nao o saldo
     * liquido. Transacoes sem categoria (tipo nulo, decisao CP-2)
     * permanecem incluidas: sao gasto de status desconhecido, nao receita
     * conhecida.
     */
    private List<CategoryTotalProjection> apenasDespesas(List<CategoryTotalProjection> categorias) {
        return categorias.stream()
                .filter(c -> !CategoryType.RECEITA.name().equals(c.getTipo()))
                .toList();
    }

    private BigDecimal somaTotais(List<CategoryTotalProjection> categorias) {
        return categorias.stream()
                .map(CategoryTotalProjection::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String buildUserPrompt(String yearMonth, List<CategoryTotalProjection> despesasAtual,
                                    BigDecimal totalAtual, BigDecimal totalAnterior) {
        String quebraPorCategoria = despesasAtual.stream()
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
                %s""".formatted(yearMonth, totalAtual, linhaMesAnterior,
                quebraPorCategoria.isBlank() ? "(nenhuma despesa registrada)" : quebraPorCategoria);
    }
}
