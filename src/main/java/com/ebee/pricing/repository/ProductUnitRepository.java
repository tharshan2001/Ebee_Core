package com.ebee.pricing.repository;

import com.ebee.pricing.entity.ProductUnit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductUnitRepository extends JpaRepository<ProductUnit, UUID> {
    List<ProductUnit> findByProductIdOrderByUnitsPerPackAsc(UUID productId);
    Optional<ProductUnit> findByProductIdAndUnitId(UUID productId, UUID unitId);
}
