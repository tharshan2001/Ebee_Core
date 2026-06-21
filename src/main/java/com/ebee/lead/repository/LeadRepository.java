package com.ebee.lead.repository;

import com.ebee.lead.entity.Lead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LeadRepository extends JpaRepository<Lead, UUID> {

    List<Lead> findByAgentIdOrderBySubmittedAtDesc(UUID agentId);
}
