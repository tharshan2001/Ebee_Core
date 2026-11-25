package com.ebee.pricing.repository;

import com.ebee.pricing.entity.PriceGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PriceGroupRepository extends JpaRepository<PriceGroup, UUID> {
    List<PriceGroup> findAllByOrderByNameAsc();
    boolean existsByNameIgnoreCase(String name);
}
