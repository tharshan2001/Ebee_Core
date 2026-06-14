package com.ebee.agent.repository;

import com.ebee.agent.entity.AgentCodeSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AgentCodeSequenceRepository extends JpaRepository<AgentCodeSequence, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM AgentCodeSequence s WHERE s.district = :district")
    Optional<AgentCodeSequence> findByDistrictWithLock(@Param("district") String district);
}
