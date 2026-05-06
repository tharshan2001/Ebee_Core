package com.ebee.stock.repository;

import com.ebee.stock.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StockRepository extends JpaRepository<Stock, UUID> {

    List<Stock> findByProductIdOrderByCreatedAtDesc(UUID productId);

    List<Stock> findByProductIdAndMovementTypeOrderByCreatedAtDesc(UUID productId, com.ebee.stock.enums.StockMovementType movementType);
}
