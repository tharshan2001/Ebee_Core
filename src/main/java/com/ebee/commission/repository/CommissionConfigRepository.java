package com.ebee.commission.repository;

import com.ebee.commission.entity.CommissionConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CommissionConfigRepository extends JpaRepository<CommissionConfig, UUID> {

    List<CommissionConfig> findByIsActiveTrue();
    List<CommissionConfig> findByAppliesTo(String appliesTo);
    List<CommissionConfig> findByEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqual(
            java.time.LocalDateTime from, java.time.LocalDateTime to);
}
