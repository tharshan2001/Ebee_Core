package com.ebee.agent.application.repository;

import com.ebee.agent.application.entity.AgentApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AgentApplicationRepository extends JpaRepository<AgentApplication, UUID> {
}
