package com.ebee.commission.repository;

import com.ebee.commission.entity.Commission;
import com.ebee.commission.enums.CommissionStatus;
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
public interface CommissionRepository extends JpaRepository<Commission, UUID> {

    List<Commission> findByAgentId(UUID agentId);
    List<Commission> findByStatus(CommissionStatus status);
    List<Commission> findByOrderId(UUID orderId);
    Optional<Commission> findCommissionByOrderId(UUID orderId);
    List<Commission> findByAgentIdAndStatus(UUID agentId, CommissionStatus status);
    boolean existsByOrderId(UUID orderId);

    @Query("SELECT COALESCE(SUM(c.commissionAmount), 0) FROM Commission c WHERE c.agent.id = :agentId AND c.status NOT IN ('CANCELLED', 'REJECTED')")
    BigDecimal sumCommissionAmountByAgentId(@Param("agentId") UUID agentId);

    @Query("SELECT COALESCE(SUM(c.commissionAmount), 0) FROM Commission c WHERE c.agent.id = :agentId AND c.status NOT IN ('CANCELLED', 'REJECTED') AND c.createdAt >= :start AND c.createdAt < :end")
    BigDecimal sumCommissionAmountByAgentIdAndDateRange(@Param("agentId") UUID agentId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(c.orderAmount), 0) FROM Commission c WHERE c.agent.id = :agentId AND c.status NOT IN ('CANCELLED', 'REJECTED')")
    BigDecimal sumSalesByAgentId(@Param("agentId") UUID agentId);

    @Query("SELECT COALESCE(SUM(c.commissionAmount), 0) FROM Commission c WHERE c.status NOT IN ('CANCELLED', 'REJECTED')")
    BigDecimal sumAllCommissionLocked();

    @Query("SELECT COALESCE(SUM(c.commissionAmount), 0) FROM Commission c WHERE c.status NOT IN ('CANCELLED', 'REJECTED') AND c.createdAt >= :start AND c.createdAt < :end")
    BigDecimal sumCommissionLockedByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
