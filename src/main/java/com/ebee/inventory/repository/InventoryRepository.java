package com.ebee.inventory.repository;

import com.ebee.inventory.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    Optional<Inventory> findByProductIdAndLocationId(UUID productId, UUID locationId);

    List<Inventory> findAllByProductId(UUID productId);

    @Query(value = "SELECT i FROM Inventory i JOIN FETCH i.product p LEFT JOIN FETCH i.location l "
            + "WHERE (:anyLocation = true OR l.id = :locationId) AND (LOWER(p.name) LIKE :q OR LOWER(p.itemCode) LIKE :q) "
            + "ORDER BY p.name, i.id",
           countQuery = "SELECT COUNT(i) FROM Inventory i JOIN i.product p LEFT JOIN i.location l "
            + "WHERE (:anyLocation = true OR l.id = :locationId) AND (LOWER(p.name) LIKE :q OR LOWER(p.itemCode) LIKE :q)")
    org.springframework.data.domain.Page<Inventory> search(@Param("q") String q, @Param("anyLocation") boolean anyLocation,
            @Param("locationId") UUID locationId, org.springframework.data.domain.Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.product.id = :productId AND i.location.id = :locationId")
    Optional<Inventory> findByProductIdWithLock(@Param("productId") UUID productId, @Param("locationId") UUID locationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    // ORDER BY makes concurrent transactions lock rows in the same order (no deadlocks).
    @Query("SELECT i FROM Inventory i WHERE i.location.id = :locationId AND i.product.id IN :productIds ORDER BY i.product.id")
    List<Inventory> findByProductIdInWithLock(@Param("productIds") Collection<UUID> productIds,
                                              @Param("locationId") UUID locationId);

    List<Inventory> findByProductIdInAndLocationId(Collection<UUID> productIds, UUID locationId);

    @Query("SELECT i FROM Inventory i LEFT JOIN FETCH i.product WHERE i.quantity - i.reservedQuantity <= i.reorderLevel AND i.reorderLevel IS NOT NULL")
    List<Inventory> findLowStockInventories();

    @Query("SELECT i FROM Inventory i LEFT JOIN FETCH i.product WHERE i.reorderLevel IS NOT NULL AND i.quantity - i.reservedQuantity <= i.reorderLevel")
    List<Inventory> findByQuantityLessThanOrEqualToReorderLevel();

    @Query("SELECT i FROM Inventory i LEFT JOIN FETCH i.product")
    List<Inventory> findAllWithProduct();
}