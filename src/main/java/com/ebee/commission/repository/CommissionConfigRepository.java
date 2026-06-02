package com.ebee.commission.repository;

import com.ebee.commission.entity.CommissionConfig;
import com.ebee.commission.enums.CommissionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommissionConfigRepository extends JpaRepository<CommissionConfig, UUID> {

    List<CommissionConfig> findByIsActiveTrue();
    List<CommissionConfig> findByType(CommissionType type);
    List<CommissionConfig> findByIsActiveTrueAndType(CommissionType type);
    Optional<CommissionConfig> findFirstByTypeAndIsActiveTrue(CommissionType type);
    List<CommissionConfig> findByAppliesTo(String appliesTo);
    List<CommissionConfig> findByEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqual(
            java.time.LocalDateTime from, java.time.LocalDateTime to);
}
