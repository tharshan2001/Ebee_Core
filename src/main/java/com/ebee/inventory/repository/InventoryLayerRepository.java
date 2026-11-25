package com.ebee.inventory.repository;

import com.ebee.inventory.entity.InventoryLayer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryLayerRepository extends JpaRepository<InventoryLayer, UUID> {

    List<InventoryLayer> findByInventoryIdOrderByReceivedAtAsc(UUID inventoryId);

    Page<InventoryLayer> findByInventoryIdOrderByReceivedAtAsc(UUID inventoryId, Pageable pageable);

    Optional<InventoryLayer> findTopByInventoryIdOrderByReceivedAtDesc(UUID inventoryId);

    List<InventoryLayer> findByInventoryIdAndRemainingQuantityGreaterThanOrderByReceivedAtAsc(UUID inventoryId, int remainingQuantity);

    List<InventoryLayer> findByInventoryIdIn(List<UUID> inventoryIds);

    List<InventoryLayer> findByInventoryIdInAndRemainingQuantityGreaterThan(List<UUID> inventoryIds, int remainingQuantity);

    /** Value of stock on hand at cost: Σ remaining × unit cost (current, not as of a past date). */
    @org.springframework.data.jpa.repository.Query(
            "SELECT COALESCE(SUM(l.remainingQuantity * l.unitCost), 0) FROM InventoryLayer l WHERE l.remainingQuantity > 0")
    java.math.BigDecimal currentStockValue();
}
