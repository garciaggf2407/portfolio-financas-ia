package com.portfolio.financas.ai;

import com.portfolio.financas.category.Category;
import com.portfolio.financas.category.CategoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Categoriza a descricao de uma transacao via Groq API (T-4.1.1). Nao
 * persiste nada -- apenas resolve qual Category (existente no sistema) e a
 * mais provavel para uma descricao. A aplicacao do resultado a uma
 * transacao especifica, incluindo a regra de nao sobrescrever categorizacao
 * manual, e responsabilidade de AutoCategorizationApplier (T-4.1.2).
 */
@Service
public class TransactionCategorizationService {

    private static final String SYSTEM_PROMPT = """
            Voce e um classificador de transacoes financeiras. Sua unica tarefa
            e escolher, dentre uma lista de categorias fornecida, a categoria
            mais provavel para a descricao de uma transacao bancaria.
            Responda APENAS com o nome exato de uma categoria da lista, sem
            explicacoes, sem pontuacao adicional, sem aspas.""";

    private final GroqClient groqClient;
    private final CategoryRepository categoryRepository;

    public TransactionCategorizationService(GroqClient groqClient, CategoryRepository categoryRepository) {
        this.groqClient = groqClient;
        this.categoryRepository = categoryRepository;
    }

    /**
     * @param descricao descricao da transacao a categorizar
     * @return a Category existente mais provavel, segundo a IA
     * @throws CategorizationApiException se a chamada a Groq API falhar
     *                                    (timeout, rate limit, erro HTTP) ou
     *                                    se a IA retornar um texto que nao
     *                                    corresponde a nenhuma categoria
     *                                    cadastrada -- capturavel pelo
     *                                    consumer da fila (T-4.3.1) para retry
     */
    public Category categorize(String descricao) {
        List<Category> categorias = categoryRepository.findAllByOrderByNomeAsc();
        if (categorias.isEmpty()) {
            throw new CategorizationApiException("Nenhuma categoria cadastrada no sistema.");
        }

        String userPrompt = buildUserPrompt(descricao, categorias);
        String raw;
        try {
            raw = groqClient.chat(SYSTEM_PROMPT, userPrompt, 0.0, 20);
        } catch (GroqApiException e) {
            throw new CategorizationApiException("Falha ao categorizar transacao via IA.", e);
        }

        return matchCategory(raw, categorias)
                .orElseThrow(() -> new CategorizationApiException(
                        "IA retornou categoria nao reconhecida: '" + raw + "'."));
    }

    private String buildUserPrompt(String descricao, List<Category> categorias) {
        String lista = categorias.stream().map(Category::getNome).collect(Collectors.joining(", "));
        return """
                Categorias disponiveis: %s

                Descricao da transacao: "%s"

                Responda apenas com o nome exato de uma das categorias acima.""".formatted(lista, descricao);
    }

    private Optional<Category> matchCategory(String raw, List<Category> categorias) {
        String cleaned = raw.strip().replaceAll("^[\"']+|[\"'.]+$", "");

        for (Category categoria : categorias) {
            if (categoria.getNome().equalsIgnoreCase(cleaned)) {
                return Optional.of(categoria);
            }
        }
        // Fallback tolerante: a IA por vezes responde com texto extra em volta
        // do nome da categoria (ex: "Categoria: Alimentacao").
        String cleanedLower = cleaned.toLowerCase(Locale.ROOT);
        for (Category categoria : categorias) {
            if (cleanedLower.contains(categoria.getNome().toLowerCase(Locale.ROOT))) {
                return Optional.of(categoria);
            }
        }
        return Optional.empty();
    }
}
