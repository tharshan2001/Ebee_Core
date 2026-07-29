package com.ebee.accounting.repository;

import com.ebee.accounting.entity.AgentBalanceCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentBalanceCacheRepository extends JpaRepository<AgentBalanceCache, UUID> {

    Optional<AgentBalanceCache> findByAgentId(UUID agentId);
}
