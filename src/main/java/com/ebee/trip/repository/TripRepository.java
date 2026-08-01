package com.ebee.trip.repository;

import com.ebee.trip.entity.Trip;
import com.ebee.trip.enums.TripStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TripRepository extends JpaRepository<Trip, UUID> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);

    Optional<Trip> findByName(String name);

    List<Trip> findByDriverId(UUID driverId);

    List<Trip> findByDriverIdAndStatusIn(UUID driverId, List<TripStatus> statuses);

    List<Trip> findByDriverIdAndStatusNotIn(UUID driverId, List<TripStatus> excluded);

    List<Trip> findByStatusNotIn(List<TripStatus> excluded);

    @Query("SELECT t FROM Trip t WHERE t.driver.id = :driverId AND t.status = :status")
    Optional<Trip> findByDriverIdAndStatus(@Param("driverId") UUID driverId, @Param("status") TripStatus status);

    Optional<Trip> findById(UUID id);
}
