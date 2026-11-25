package com.ebee.customer.repository;

import com.ebee.customer.entity.CustomerAdvanceTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface CustomerAdvanceTransactionRepository extends JpaRepository<CustomerAdvanceTransaction, UUID> {

    Page<CustomerAdvanceTransaction> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    List<CustomerAdvanceTransaction> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM CustomerAdvanceTransaction t WHERE t.customer.id = :customerId")
    BigDecimal sumAdvanceByCustomerId(@Param("customerId") UUID customerId);

    @Query("SELECT t FROM CustomerAdvanceTransaction t WHERE t.customer.id = :customerId ORDER BY t.createdAt DESC LIMIT 1")
    CustomerAdvanceTransaction findLatestByCustomerId(@Param("customerId") UUID customerId);

    /** Total customer advance (wallet) balances before the cutoff — money we hold for customers. */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM CustomerAdvanceTransaction t WHERE t.createdAt < :before")
    BigDecimal sumAllBefore(@Param("before") java.time.LocalDateTime before);
}
