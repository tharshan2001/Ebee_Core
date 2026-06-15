package com.ebee.commission.repository;

import com.ebee.commission.entity.Commission;
import com.ebee.commission.enums.CommissionStatus;
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
public interface CommissionRepository extends JpaRepository<Commission, UUID> {

    List<Commission> findByAgentId(UUID agentId);
    List<Commission> findByStatus(CommissionStatus status);
    List<Commission> findByOrderId(UUID orderId);
    Optional<Commission> findCommissionByOrderId(UUID orderId);
    List<Commission> findByAgentIdAndStatus(UUID agentId, CommissionStatus status);
    boolean existsByOrderId(UUID orderId);

    @Query("SELECT c FROM Commission c JOIN FETCH c.agent a JOIN FETCH a.user")
    List<Commission> findAllWithDetails();

    @Query(value = "SELECT c FROM Commission c JOIN FETCH c.agent a JOIN FETCH a.user",
           countQuery = "SELECT COUNT(c) FROM Commission c")
    Page<Commission> findAllWithDetails(Pageable pageable);

    @Query("SELECT c FROM Commission c JOIN FETCH c.agent a JOIN FETCH a.user WHERE c.status = :status")
    List<Commission> findByStatusWithDetails(@Param("status") CommissionStatus status);

    @Query("SELECT c FROM Commission c JOIN FETCH c.agent a JOIN FETCH a.user WHERE c.agent.id = :agentId")
    List<Commission> findByAgentIdWithDetails(@Param("agentId") UUID agentId);

    @Query("SELECT c FROM Commission c JOIN FETCH c.agent a JOIN FETCH a.user WHERE c.id = :id")
    Optional<Commission> findByIdWithDetails(@Param("id") UUID id);

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

    @Query("SELECT c.agent.id, COALESCE(SUM(c.commissionAmount), 0) FROM Commission c WHERE c.agent.id IN :agentIds AND c.status NOT IN ('CANCELLED', 'REJECTED') AND c.createdAt >= :start AND c.createdAt < :end GROUP BY c.agent.id")
    List<Object[]> sumCommissionByAgentIds(@Param("agentIds") List<UUID> agentIds, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT c.agent.id, " +
           "COALESCE(SUM(c.commissionAmount), 0), " +
           "COALESCE(SUM(CASE WHEN c.status = 'PAID' THEN c.commissionAmount ELSE 0 END), 0), " +
           "COALESCE(SUM(CASE WHEN c.status IN ('PROCESSING', 'APPROVED') THEN c.commissionAmount ELSE 0 END), 0) " +
           "FROM Commission c " +
           "WHERE c.agent.id IN :agentIds AND c.status NOT IN ('CANCELLED', 'REJECTED') " +
           "GROUP BY c.agent.id")
    List<Object[]> findCommissionSummaryByAgentIds(@Param("agentIds") List<UUID> agentIds);

    @Query("SELECT c.agent.id, COALESCE(SUM(c.orderAmount), 0) FROM Commission c WHERE c.agent.id IN :agentIds AND c.status NOT IN ('CANCELLED', 'REJECTED') GROUP BY c.agent.id")
    List<Object[]> sumSalesByAgentIds(@Param("agentIds") List<UUID> agentIds);

    @Query("SELECT c FROM Commission c WHERE c.status = 'APPROVED' AND c.approvedAt IS NOT NULL AND c.approvedAt <= :threshold")
    List<Commission> findApprovedCommissionsOlderThan(@Param("threshold") LocalDateTime threshold);

    @Query("SELECT COALESCE(SUM(c.commissionAmount), 0) FROM Commission c WHERE c.status = :status")
    BigDecimal sumCommissionAmountByStatus(@Param("status") CommissionStatus status);

    @Query("SELECT COALESCE(SUM(c.commissionAmount), 0) FROM Commission c WHERE c.agent.id = :agentId AND c.status = :status")
    BigDecimal sumCommissionAmountByAgentIdAndStatus(@Param("agentId") UUID agentId, @Param("status") CommissionStatus status);
}
