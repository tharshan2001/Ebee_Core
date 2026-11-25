package com.ebee.pricing.repository;

import com.ebee.pricing.entity.Unit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UnitRepository extends JpaRepository<Unit, UUID> {
    List<Unit> findAllByOrderByNameAsc();
    boolean existsByNameIgnoreCase(String name);
}
