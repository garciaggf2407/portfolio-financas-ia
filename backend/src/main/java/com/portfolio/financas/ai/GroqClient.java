package com.portfolio.financas.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

/**
 * Cliente de baixo nivel para o endpoint de chat completions da Groq API
 * (compativel com o formato OpenAI, https://api.groq.com/openai/v1/chat/completions).
 * Usado por TransactionCategorizationService (categorizacao, T-4.1.1) e
 * MonthlySummaryService (resumo mensal, T-4.2.1): a chamada HTTP,
 * autenticacao e tratamento de timeout/erro sao identicos nos dois casos,
 * apenas o prompt e o uso do texto de resposta mudam. Pacote-privado --
 * detalhe de implementacao do pacote ai, nao exposto fora dele.
 */
@Component
class GroqClient {

    private final RestClient restClient;
    private final String model;

    GroqClient(@Value("${groq.api.key}") String apiKey,
               @Value("${groq.api.url}") String apiUrl,
               @Value("${groq.api.model}") String model,
               @Value("${groq.api.timeout-connect-ms:5000}") int connectTimeoutMs,
               @Value("${groq.api.timeout-read-ms:20000}") int readTimeoutMs) {
        this.model = model;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Chama o endpoint de chat completions e retorna o texto da resposta
     * (choices[0].message.content), sem espacos nas bordas.
     *
     * @throws GroqApiException em qualquer falha de rede, timeout, status de
     *                          erro HTTP (ex: 429 rate limit do free tier) ou
     *                          resposta vazia/malformada.
     */
    String chat(String systemPrompt, String userPrompt, double temperature, int maxTokens) {
        ChatRequest request = new ChatRequest(model, List.of(
                new ChatMessage("system", systemPrompt),
                new ChatMessage("user", userPrompt)), temperature, maxTokens);

        ChatResponse response;
        try {
            response = restClient.post()
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);
        } catch (RestClientResponseException e) {
            throw new GroqApiException(
                    "Groq API respondeu com erro HTTP " + e.getStatusCode().value() + ".", e);
        } catch (ResourceAccessException e) {
            throw new GroqApiException("Timeout ou falha de conexao ao chamar a Groq API.", e);
        } catch (RestClientException e) {
            throw new GroqApiException("Erro inesperado ao chamar a Groq API.", e);
        }

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new GroqApiException("Groq API retornou resposta sem 'choices'.");
        }
        String content = response.choices().get(0).message().content();
        if (content == null || content.isBlank()) {
            throw new GroqApiException("Groq API retornou conteudo vazio.");
        }
        return content.trim();
    }

    private record ChatMessage(String role, String content) {
    }

    private record ChatRequest(
            String model,
            List<ChatMessage> messages,
            double temperature,
            @JsonProperty("max_tokens") int maxTokens) {
    }

    private record ChatResponse(List<Choice> choices) {
    }

    private record Choice(ChatMessage message) {
    }
}
