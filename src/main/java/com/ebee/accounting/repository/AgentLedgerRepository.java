package com.ebee.accounting.repository;

import com.ebee.accounting.entity.AgentLedger;
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
public interface AgentLedgerRepository extends JpaRepository<AgentLedger, UUID> {

    List<AgentLedger> findByAgentIdOrderByCreatedAtDesc(UUID agentId);

    Page<AgentLedger> findByAgentId(UUID agentId, Pageable pageable);

    List<AgentLedger> findByCommissionId(UUID commissionId);

    List<AgentLedger> findByPayoutId(UUID payoutId);

    List<AgentLedger> findByAgentIdAndCreatedAtBetween(UUID agentId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT COALESCE(SUM(l.credit) - SUM(l.debit), 0) FROM AgentLedger l WHERE l.agentId = :agentId")
    BigDecimal calculateBalance(@Param("agentId") UUID agentId);

    @Query("SELECT l FROM AgentLedger l WHERE l.agentId = :agentId ORDER BY l.createdAt DESC LIMIT 1")
    Optional<AgentLedger> findLatestByAgentId(@Param("agentId") UUID agentId);
}
