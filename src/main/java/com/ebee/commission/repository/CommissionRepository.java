package com.ebee.commission.repository;

import com.ebee.commission.entity.Commission;
import com.ebee.commission.enums.CommissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommissionRepository extends JpaRepository<Commission, UUID> {

    List<Commission> findByAgentId(UUID agentId);
    List<Commission> findByStatus(CommissionStatus status);
    List<Commission> findByOrderId(UUID orderId);
    Optional<Commission> findCommissionByOrderId(UUID orderId);
    List<Commission> findByAgentIdAndStatus(UUID agentId, CommissionStatus status);
    boolean existsByOrderId(UUID orderId);
}
