package com.ebee.trip.repository;

import com.ebee.trip.entity.Trip;
import com.ebee.trip.enums.TripStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TripRepository extends JpaRepository<Trip, UUID> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);

    List<Trip> findByDriverIdAndStatusIn(UUID driverId, List<TripStatus> statuses);

    @Query("SELECT t FROM Trip t WHERE t.driver.id = :driverId AND t.status = :status")
    Optional<Trip> findByDriverIdAndStatus(@Param("driverId") UUID driverId, @Param("status") TripStatus status);

    Optional<Trip> findById(UUID id);

    @Query("SELECT t FROM Trip t WHERE (:status IS NULL OR t.status = :status) " +
            "AND (:driverId IS NULL OR t.driver.id = :driverId) " +
            "AND t.startDate >= COALESCE(:dateFrom, CAST('0001-01-01' AS date)) " +
            "AND t.endDate <= COALESCE(:dateTo, CAST('9999-12-31' AS date))")
    List<Trip> search(@Param("status") TripStatus status,
                      @Param("driverId") UUID driverId,
                      @Param("dateFrom") LocalDate dateFrom,
                      @Param("dateTo") LocalDate dateTo);

    @Query("SELECT t FROM Trip t WHERE (:status IS NULL OR t.status = :status) " +
            "AND (:driverId IS NULL OR t.driver.id = :driverId) " +
            "AND t.startDate >= COALESCE(:dateFrom, CAST('0001-01-01' AS date)) " +
            "AND t.endDate <= COALESCE(:dateTo, CAST('9999-12-31' AS date))")
    Page<Trip> search(@Param("status") TripStatus status,
                      @Param("driverId") UUID driverId,
                      @Param("dateFrom") LocalDate dateFrom,
                      @Param("dateTo") LocalDate dateTo,
                      Pageable pageable);
}
