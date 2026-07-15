package com.ebee.combo.repository;

import com.ebee.combo.entity.Combo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ComboRepository extends JpaRepository<Combo, UUID> {

    @EntityGraph(attributePaths = {"items", "items.product", "rule", "rule.freeProduct"})
    Optional<Combo> findByIdWithDetails(@Param("id") UUID id);

    Page<Combo> findAllByIsActiveTrue(Pageable pageable);
}
