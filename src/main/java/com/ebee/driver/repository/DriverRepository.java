package com.ebee.driver.repository;

import com.ebee.driver.entity.Driver;
import com.ebee.driver.enums.DriverAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DriverRepository extends JpaRepository<Driver, UUID> {
    Optional<Driver> findByUserId(UUID userId);
    List<Driver> findByAvailability(DriverAvailability availability);
}
