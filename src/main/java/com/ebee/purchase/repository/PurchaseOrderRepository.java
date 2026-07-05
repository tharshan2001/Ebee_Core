package com.ebee.purchase.repository;

import com.ebee.purchase.entity.PurchaseOrder;
import com.ebee.purchase.enums.PurchaseOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    @Query("SELECT po FROM PurchaseOrder po LEFT JOIN FETCH po.supplier LEFT JOIN FETCH po.items i LEFT JOIN FETCH i.product WHERE po.id = :id")
    Optional<PurchaseOrder> findByIdWithItems(@Param("id") UUID id);

    @Query("SELECT po FROM PurchaseOrder po LEFT JOIN FETCH po.supplier LEFT JOIN FETCH po.attachments WHERE po.id = :id")
    Optional<PurchaseOrder> findByIdWithAttachments(@Param("id") UUID id);

    Page<PurchaseOrder> findBySupplierId(UUID supplierId, Pageable pageable);

    Page<PurchaseOrder> findByPoNumberStartingWith(String prefix, Pageable pageable);

    Page<PurchaseOrder> findByStatus(PurchaseOrderStatus status, Pageable pageable);

    long countByPoNumberStartingWith(String prefix);

    Optional<PurchaseOrder> findByPoNumber(String poNumber);
}
