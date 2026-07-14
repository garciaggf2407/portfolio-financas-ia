package com.portfolio.financas.common;

import com.portfolio.financas.ai.GroqApiException;
import com.portfolio.financas.messaging.DeadLetterQueueUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void resourceNotFoundVira404() {
        ResponseEntity<ErrorResponse> response =
                handler.handleNotFound(new ResourceNotFoundException("Transacao nao encontrada"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().message()).isEqualTo("Transacao nao encontrada");
    }

    @Test
    void duplicateResourceVira409() {
        ResponseEntity<ErrorResponse> response =
                handler.handleDuplicate(new DuplicateResourceException("Categoria ja existe"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().status()).isEqualTo(409);
    }

    @Test
    void invalidRequestVira400() {
        ResponseEntity<ErrorResponse> response =
                handler.handleInvalid(new InvalidRequestException("nome e obrigatorio"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().status()).isEqualTo(400);
    }

    @Test
    void typeMismatchVira400EIncluiONomeDoParametroNaMensagem() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("months");
        when(ex.getValue()).thenReturn("abc");

        ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).contains("months").contains("abc");
    }

    @Test
    void groqApiExceptionVira503() {
        ResponseEntity<ErrorResponse> response =
                handler.handleGroqApiFailure(new GroqApiException("timeout na Groq API"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().status()).isEqualTo(503);
    }

    @Test
    void deadLetterQueueUnavailableVira503() {
        ResponseEntity<ErrorResponse> response = handler.handleDeadLetterQueueUnavailable(
                new DeadLetterQueueUnavailableException("RabbitMQ indisponivel", new RuntimeException("conexao recusada")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().status()).isEqualTo(503);
    }
}
