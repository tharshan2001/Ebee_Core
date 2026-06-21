package com.ebee.agent.application.repository;

import com.ebee.agent.application.entity.AgentApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AgentApplicationRepository extends JpaRepository<AgentApplication, UUID> {
}
