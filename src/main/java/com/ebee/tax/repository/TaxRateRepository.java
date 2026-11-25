package com.ebee.tax.repository;

import com.ebee.tax.entity.TaxRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TaxRateRepository extends JpaRepository<TaxRate, UUID> {
    List<TaxRate> findAllByOrderByRateDesc();
    boolean existsByNameIgnoreCase(String name);
}
