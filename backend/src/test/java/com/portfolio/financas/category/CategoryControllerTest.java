package com.portfolio.financas.category;

import com.portfolio.financas.category.dto.CategoryResponse;
import com.portfolio.financas.category.dto.CreateCategoryRequest;
import com.portfolio.financas.common.DuplicateResourceException;
import com.portfolio.financas.common.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryControllerTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Test
    void listSemFiltroRetornaTodasAsCategoriasOrdenadasPorNome() {
        Category alimentacao = new Category("Alimentacao", CategoryType.DESPESA);
        when(categoryRepository.findAllByOrderByNomeAsc()).thenReturn(List.of(alimentacao));
        CategoryController controller = new CategoryController(categoryRepository);

        List<CategoryResponse> result = controller.list(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).nome()).isEqualTo("Alimentacao");
        verify(categoryRepository, never()).findByTipoOrderByNomeAsc(any());
    }

    @Test
    void listComFiltroDeTipoDelegaParaORepositorioCorreto() {
        Category salario = new Category("Salario", CategoryType.RECEITA);
        when(categoryRepository.findByTipoOrderByNomeAsc(CategoryType.RECEITA)).thenReturn(List.of(salario));
        CategoryController controller = new CategoryController(categoryRepository);

        List<CategoryResponse> result = controller.list(CategoryType.RECEITA);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).tipo()).isEqualTo(CategoryType.RECEITA);
        verify(categoryRepository, never()).findAllByOrderByNomeAsc();
    }

    @Test
    void createComDadosValidosPersisteEDevolveACategoriaCriada() {
        when(categoryRepository.existsByNomeIgnoreCase("Lazer")).thenReturn(false);
        CategoryController controller = new CategoryController(categoryRepository);

        CategoryResponse response = controller.create(new CreateCategoryRequest("Lazer", CategoryType.DESPESA));

        assertThat(response.nome()).isEqualTo("Lazer");
        assertThat(response.tipo()).isEqualTo(CategoryType.DESPESA);

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        assertThat(captor.getValue().getNome()).isEqualTo("Lazer");
    }

    @Test
    void createRemoveEspacosDasBordasDoNome() {
        when(categoryRepository.existsByNomeIgnoreCase("Lazer")).thenReturn(false);
        CategoryController controller = new CategoryController(categoryRepository);

        controller.create(new CreateCategoryRequest("  Lazer  ", CategoryType.DESPESA));

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        assertThat(captor.getValue().getNome()).isEqualTo("Lazer");
    }

    @Test
    void createComNomeJaExistenteLancaDuplicateResourceException() {
        when(categoryRepository.existsByNomeIgnoreCase("Lazer")).thenReturn(true);
        CategoryController controller = new CategoryController(categoryRepository);

        assertThatThrownBy(() -> controller.create(new CreateCategoryRequest("Lazer", CategoryType.DESPESA)))
                .isInstanceOf(DuplicateResourceException.class);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void createComNomeVazioLancaInvalidRequestException() {
        CategoryController controller = new CategoryController(categoryRepository);

        assertThatThrownBy(() -> controller.create(new CreateCategoryRequest("   ", CategoryType.DESPESA)))
                .isInstanceOf(InvalidRequestException.class);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void createComNomeNuloLancaInvalidRequestException() {
        CategoryController controller = new CategoryController(categoryRepository);

        assertThatThrownBy(() -> controller.create(new CreateCategoryRequest(null, CategoryType.DESPESA)))
                .isInstanceOf(InvalidRequestException.class);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void createComTipoNuloLancaInvalidRequestException() {
        CategoryController controller = new CategoryController(categoryRepository);

        assertThatThrownBy(() -> controller.create(new CreateCategoryRequest("Lazer", null)))
                .isInstanceOf(InvalidRequestException.class);
        verify(categoryRepository, never()).save(any());
    }
}
