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

    @Query("SELECT DISTINCT po FROM PurchaseOrder po LEFT JOIN FETCH po.supplier LEFT JOIN FETCH po.createdBy WHERE po.supplier.id = :supplierId")
    Page<PurchaseOrder> findBySupplierId(@Param("supplierId") UUID supplierId, Pageable pageable);

    @Query("SELECT DISTINCT po FROM PurchaseOrder po LEFT JOIN FETCH po.supplier LEFT JOIN FETCH po.createdBy WHERE po.poNumber LIKE CONCAT(:prefix, '%')")
    Page<PurchaseOrder> findByPoNumberStartingWith(@Param("prefix") String prefix, Pageable pageable);

    @Query("SELECT DISTINCT po FROM PurchaseOrder po LEFT JOIN FETCH po.supplier LEFT JOIN FETCH po.createdBy WHERE po.status = :status")
    Page<PurchaseOrder> findByStatus(@Param("status") PurchaseOrderStatus status, Pageable pageable);

    @Query("SELECT DISTINCT po FROM PurchaseOrder po LEFT JOIN FETCH po.supplier LEFT JOIN FETCH po.createdBy")
    Page<PurchaseOrder> findAllWithDetails(Pageable pageable);

    long countByPoNumberStartingWith(String prefix);

    Optional<PurchaseOrder> findByPoNumber(String poNumber);
}
