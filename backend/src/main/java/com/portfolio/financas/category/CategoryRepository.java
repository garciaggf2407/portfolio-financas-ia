package com.portfolio.financas.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    boolean existsByNomeIgnoreCase(String nome);

    List<Category> findByTipoOrderByNomeAsc(CategoryType tipo);

    List<Category> findAllByOrderByNomeAsc();
}
