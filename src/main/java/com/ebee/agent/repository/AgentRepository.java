package com.ebee.agent.repository;

import com.ebee.agent.entity.Agent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query("SELECT COUNT(a) FROM Agent a WHERE a.createdAt >= :start AND a.createdAt < :end")
    Long countNewAgentsSince(@Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

    @Query("SELECT COALESCE(a.district, 'Unknown'), COUNT(a), SUM(CASE WHEN a.isActive = true THEN 1 ELSE 0 END) FROM Agent a GROUP BY a.district ORDER BY COUNT(a) DESC")
    List<Object[]> findAgentDistrictDistribution();

    @Query("SELECT a FROM Agent a LEFT JOIN FETCH a.user")
    Page<Agent> findAllWithUser(Pageable pageable);

    @Query("SELECT a FROM Agent a LEFT JOIN FETCH a.user WHERE a.agentCode LIKE %:search% OR a.user.firstName LIKE %:search% OR a.user.lastName LIKE %:search% OR a.user.phoneNumber LIKE %:search% OR a.nicNumber LIKE %:search% OR a.district LIKE %:search% OR a.user.email LIKE %:search%")
    Page<Agent> findBySearchTerm(@Param("search") String search, Pageable pageable);

    @Query("SELECT a FROM Agent a LEFT JOIN FETCH a.user WHERE a.agentCode LIKE %:search% OR a.user.firstName LIKE %:search% OR a.user.lastName LIKE %:search% OR a.user.phoneNumber LIKE %:search% OR a.nicNumber LIKE %:search% OR a.district LIKE %:search% OR a.user.email LIKE %:search%")
    List<Agent> findBySearchTerm(@Param("search") String search);
}
