package com.ebee.stock.repository;

import com.ebee.stock.entity.Stock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StockRepository extends JpaRepository<Stock, UUID> {

    @EntityGraph(attributePaths = {"createdBy"})
    List<Stock> findByInventoryIdOrderByCreatedAtDesc(UUID inventoryId);

    @EntityGraph(attributePaths = {"createdBy"})
    List<Stock> findByInventoryIdAndMovementTypeOrderByCreatedAtDesc(UUID inventoryId, com.ebee.stock.enums.StockMovementType movementType);
}
