package com.ebee.driver.repository;

import com.ebee.driver.entity.Driver;
import com.ebee.driver.enums.DriverAvailability;
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
public interface DriverRepository extends JpaRepository<Driver, UUID> {
    Optional<Driver> findByUserId(UUID userId);

    @Query("SELECT d FROM Driver d LEFT JOIN FETCH d.user WHERE d.availability = :availability")
    List<Driver> findByAvailability(@Param("availability") DriverAvailability availability);

    @Query(value = "SELECT d FROM Driver d LEFT JOIN FETCH d.user WHERE d.availability = :availability",
           countQuery = "SELECT COUNT(d) FROM Driver d WHERE d.availability = :availability")
    Page<Driver> findByAvailability(@Param("availability") DriverAvailability availability, Pageable pageable);

    @Query(value = "SELECT d FROM Driver d LEFT JOIN FETCH d.user",
           countQuery = "SELECT COUNT(d) FROM Driver d")
    Page<Driver> findAll(Pageable pageable);
}
