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
}
