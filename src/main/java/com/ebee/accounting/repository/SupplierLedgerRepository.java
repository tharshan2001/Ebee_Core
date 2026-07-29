package com.ebee.accounting.repository;

import com.ebee.accounting.entity.SupplierLedger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupplierLedgerRepository extends JpaRepository<SupplierLedger, UUID> {

    List<SupplierLedger> findBySupplierIdOrderByCreatedAtDesc(UUID supplierId);

    Page<SupplierLedger> findBySupplierId(UUID supplierId, Pageable pageable);

    List<SupplierLedger> findByPurchaseOrderId(UUID purchaseOrderId);

    List<SupplierLedger> findByExpenseId(UUID expenseId);

    List<SupplierLedger> findBySupplierIdAndCreatedAtBetween(UUID supplierId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT COALESCE(SUM(l.credit) - SUM(l.debit), 0) FROM SupplierLedger l WHERE l.supplierId = :supplierId")
    BigDecimal calculateBalance(@Param("supplierId") UUID supplierId);

    @Query("SELECT l FROM SupplierLedger l WHERE l.supplierId = :supplierId ORDER BY l.createdAt DESC LIMIT 1")
    Optional<SupplierLedger> findLatestBySupplierId(@Param("supplierId") UUID supplierId);
}
