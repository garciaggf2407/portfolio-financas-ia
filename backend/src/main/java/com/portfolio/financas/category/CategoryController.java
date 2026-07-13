package com.portfolio.financas.category;

import com.portfolio.financas.category.dto.CategoryResponse;
import com.portfolio.financas.category.dto.CreateCategoryRequest;
import com.portfolio.financas.common.DuplicateResourceException;
import com.portfolio.financas.common.InvalidRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * CRUD de categorias. O seed de categorias padrao (Alimentacao,
 * Transporte, Moradia, Lazer, Saude, Outros, Salario) ja acontece na
 * migration V1__init.sql -- nao ha necessidade de um CommandLineRunner
 * separado (ver T-2.2.1 no checkpoint-map).
 */
@RestController
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryRepository categoryRepository;

    public CategoryController(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @GetMapping
    public List<CategoryResponse> list(@RequestParam(required = false) CategoryType tipo) {
        List<Category> categories = tipo == null
                ? categoryRepository.findAllByOrderByNomeAsc()
                : categoryRepository.findByTipoOrderByNomeAsc(tipo);
        return categories.stream().map(CategoryResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@RequestBody CreateCategoryRequest request) {
        if (request == null || request.nome() == null || request.nome().isBlank()) {
            throw new InvalidRequestException("nome e obrigatorio.");
        }
        if (request.tipo() == null) {
            throw new InvalidRequestException("tipo e obrigatorio.");
        }
        String nome = request.nome().strip();
        if (categoryRepository.existsByNomeIgnoreCase(nome)) {
            throw new DuplicateResourceException("Ja existe uma categoria com o nome '" + nome + "'.");
        }

        Category category = new Category(nome, request.tipo());
        categoryRepository.save(category);
        return CategoryResponse.from(category);
    }
}
