package com.ebee.payout.repository;

import com.ebee.payout.entity.Payout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PayoutRepository extends JpaRepository<Payout, UUID> {

    List<Payout> findByAgentIdOrderByCreatedAtDesc(UUID agentId);

    @Query("SELECT p FROM Payout p JOIN p.commissionIds cid WHERE cid IN :commissionIds")
    List<Payout> findByCommissionIds(@Param("commissionIds") List<UUID> commissionIds);
}
