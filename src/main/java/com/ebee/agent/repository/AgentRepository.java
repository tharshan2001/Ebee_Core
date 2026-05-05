package com.ebee.agent.repository;

import com.ebee.agent.entity.Agent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentRepository extends JpaRepository<Agent, UUID> {

    Optional<Agent> findByUserId(UUID userId);
    boolean existsByAgentCode(String agentCode);
    Optional<Agent> findByAgentCode(String agentCode);
}
