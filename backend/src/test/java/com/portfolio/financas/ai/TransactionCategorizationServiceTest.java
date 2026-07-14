package com.portfolio.financas.ai;

import com.portfolio.financas.category.Category;
import com.portfolio.financas.category.CategoryRepository;
import com.portfolio.financas.category.CategoryType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionCategorizationServiceTest {

    @Mock
    private GroqClient groqClient;

    @Mock
    private CategoryRepository categoryRepository;

    private TransactionCategorizationService service() {
        return new TransactionCategorizationService(groqClient, categoryRepository);
    }

    @Test
    void respostaExataDaIaResolveParaACategoria() {
        Category alimentacao = new Category("Alimentacao", CategoryType.DESPESA);
        when(categoryRepository.findAllByOrderByNomeAsc()).thenReturn(List.of(alimentacao));
        when(groqClient.chat(anyString(), anyString(), anyDouble(), anyInt())).thenReturn("Alimentacao");

        Category resultado = service().categorize("Supermercado");

        assertThat(resultado).isEqualTo(alimentacao);
    }

    @Test
    void respostaComTextoExtraUsaFallbackDeSubstringQuandoInequivoco() {
        Category alimentacao = new Category("Alimentacao", CategoryType.DESPESA);
        when(categoryRepository.findAllByOrderByNomeAsc()).thenReturn(List.of(alimentacao));
        when(groqClient.chat(anyString(), anyString(), anyDouble(), anyInt()))
                .thenReturn("Categoria: Alimentacao.");

        Category resultado = service().categorize("Supermercado");

        assertThat(resultado).isEqualTo(alimentacao);
    }

    /**
     * Regressao: o fallback por substring antigo escolhia a primeira
     * categoria que desse match, mesmo com mais de uma candidata --
     * falso-positivo silencioso. Agora uma resposta ambigua falha em vez
     * de adivinhar.
     */
    @Test
    void respostaAmbiguaEntreDuasCategoriasFalhaEmVezDeEscolherAPrimeira() {
        Category saude = new Category("Saude", CategoryType.DESPESA);
        Category saudePublica = new Category("Saude Publica", CategoryType.DESPESA);
        when(categoryRepository.findAllByOrderByNomeAsc()).thenReturn(List.of(saude, saudePublica));
        when(groqClient.chat(anyString(), anyString(), anyDouble(), anyInt()))
                .thenReturn("Categoria: Saude Publica");

        // "Saude Publica" contem tanto "Saude" quanto "Saude Publica" como
        // substring -- duas candidatas, resposta ambigua.
        assertThatThrownBy(() -> service().categorize("Consulta medica"))
                .isInstanceOf(CategorizationApiException.class);
    }

    @Test
    void respostaSemCategoriaCorrespondenteLancaExcecaoEspecifica() {
        Category alimentacao = new Category("Alimentacao", CategoryType.DESPESA);
        when(categoryRepository.findAllByOrderByNomeAsc()).thenReturn(List.of(alimentacao));
        when(groqClient.chat(anyString(), anyString(), anyDouble(), anyInt())).thenReturn("Categoria Inexistente");

        assertThatThrownBy(() -> service().categorize("Algo estranho"))
                .isInstanceOf(CategorizationApiException.class);
    }

    @Test
    void semCategoriasCadastradasLancaExcecaoAntesDeChamarAIa() {
        when(categoryRepository.findAllByOrderByNomeAsc()).thenReturn(List.of());

        assertThatThrownBy(() -> service().categorize("Qualquer coisa"))
                .isInstanceOf(CategorizationApiException.class);
    }

    @Test
    void falhaDaGroqApiEEncapsuladaComoCategorizationApiException() {
        Category alimentacao = new Category("Alimentacao", CategoryType.DESPESA);
        when(categoryRepository.findAllByOrderByNomeAsc()).thenReturn(List.of(alimentacao));
        when(groqClient.chat(anyString(), anyString(), anyDouble(), anyInt()))
                .thenThrow(new GroqApiException("Timeout."));

        assertThatThrownBy(() -> service().categorize("Supermercado"))
                .isInstanceOf(CategorizationApiException.class);
    }
}
