package com.portfolio.financas.category;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.financas.category.dto.CreateCategoryRequest;
import com.portfolio.financas.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CRUD de categorias contra Postgres real (Testcontainers): exercita a
 * migration V1__init.sql (seed de categorias padrao + UNIQUE(nome)), nao
 * apenas o repositorio mockado (ver CategoryControllerTest).
 */
class CategoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void listSemFiltroRetornaAsCategoriasSeedadasPelaMigration() throws Exception {
        mockMvc.perform(get("/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nome == 'Alimentacao')]").exists())
                .andExpect(jsonPath("$[?(@.nome == 'Salario')]").exists());
    }

    @Test
    void listComFiltroDeTipoRetornaApenasCategoriasDoTipoPedido() throws Exception {
        mockMvc.perform(get("/categories").param("tipo", "RECEITA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].tipo", org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.equalTo("RECEITA"))));
    }

    @Test
    void createPersisteNoBancoRealEDevolve201() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateCategoryRequest("Viagem", CategoryType.DESPESA));

        mockMvc.perform(post("/categories").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Viagem"))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void createComNomeDuplicadoViolaAConstraintUniqueEDevolve409() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateCategoryRequest("Educacao", CategoryType.DESPESA));

        mockMvc.perform(post("/categories").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        String duplicateResponse = mockMvc.perform(
                        post("/categories").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();

        assertThat(duplicateResponse).contains("Educacao");
    }

    @Test
    void createComNomeVazioDevolve400() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateCategoryRequest("   ", CategoryType.DESPESA));

        mockMvc.perform(post("/categories").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }
}
