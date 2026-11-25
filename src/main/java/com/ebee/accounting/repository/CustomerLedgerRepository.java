package com.ebee.accounting.repository;

import com.ebee.accounting.entity.CustomerLedger;
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
public interface CustomerLedgerRepository extends JpaRepository<CustomerLedger, UUID> {

    List<CustomerLedger> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    Page<CustomerLedger> findByCustomerId(UUID customerId, Pageable pageable);

    List<CustomerLedger> findByOrderId(UUID orderId);

    List<CustomerLedger> findByPaymentId(UUID paymentId);

    List<CustomerLedger> findByReturnId(UUID returnId);

    List<CustomerLedger> findByCustomerIdAndCreatedAtBetween(UUID customerId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT COALESCE(SUM(l.debit) - SUM(l.credit), 0) FROM CustomerLedger l WHERE l.customerId = :customerId")
    BigDecimal calculateBalance(@Param("customerId") UUID customerId);

    @Query("SELECT l FROM CustomerLedger l WHERE l.customerId = :customerId ORDER BY l.createdAt DESC LIMIT 1")
    Optional<CustomerLedger> findLatestByCustomerId(@Param("customerId") UUID customerId);

    // ── Reconciliation (rows: [customerId, amount]) ──────────────────

    @Query("SELECT l.customerId, COALESCE(SUM(l.debit) - SUM(l.credit), 0) FROM CustomerLedger l GROUP BY l.customerId")
    List<Object[]> sumBalanceByCustomer();

    /** Entries not tied to an order, payment or return (manual adjustments, account-level payments). Legacy ADVANCE rows excluded. */
    @Query("SELECT l.customerId, COALESCE(SUM(l.debit) - SUM(l.credit), 0) FROM CustomerLedger l "
            + "WHERE l.orderId IS NULL AND l.paymentId IS NULL AND l.returnId IS NULL AND l.transactionType <> 'ADVANCE' "
            + "GROUP BY l.customerId")
    List<Object[]> sumUnlinkedByCustomer();

    /** Legacy ADVANCE entries (wallet credits that the old rules posted into the receivable ledger). */
    @Query("SELECT l.customerId, COUNT(l) FROM CustomerLedger l WHERE l.transactionType = 'ADVANCE' GROUP BY l.customerId")
    List<Object[]> countLegacyAdvanceByCustomer();

    /** Rows: [customerId, debit − credit] for entries before the cutoff (positive = customer owes us). */
    @Query("SELECT l.customerId, COALESCE(SUM(l.debit) - SUM(l.credit), 0) FROM CustomerLedger l "
            + "WHERE l.createdAt < :before GROUP BY l.customerId")
    List<Object[]> balancesBefore(@Param("before") LocalDateTime before);

    boolean existsByCustomerIdAndDescription(UUID customerId, String description);
}
