package com.ebee.agent.repository;

import com.ebee.agent.entity.Agent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentRepository extends JpaRepository<Agent, UUID> {

    @Query("SELECT a FROM Agent a LEFT JOIN FETCH a.user WHERE a.user.id = :userId")
    Optional<Agent> findByUserId(@Param("userId") UUID userId);

    boolean existsByUserId(UUID userId);

    boolean existsByAgentCode(String agentCode);

    @Query("SELECT a FROM Agent a LEFT JOIN FETCH a.user WHERE a.agentCode = :agentCode")
    Optional<Agent> findByAgentCode(@Param("agentCode") String agentCode);

    @Query("SELECT a FROM Agent a LEFT JOIN FETCH a.user")
    List<Agent> findAllWithUser();

    @Query("SELECT a FROM Agent a LEFT JOIN FETCH a.user WHERE a.id = :id")
    Optional<Agent> findByIdWithUser(@Param("id") UUID id);

    @Query("SELECT a FROM Agent a LEFT JOIN FETCH a.user WHERE a.id IN :ids")
    List<Agent> findAllByIdWithUser(@Param("ids") List<UUID> ids);

    @Query("SELECT MAX(a.agentCode) FROM Agent a WHERE a.agentCode LIKE :pattern")
    String findMaxAgentCodeByPattern(@Param("pattern") String pattern);
}
