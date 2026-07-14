package com.portfolio.financas.summary;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MonthlySummaryRepository extends JpaRepository<MonthlySummary, UUID> {

    Optional<MonthlySummary> findByMes(String mes);
}
