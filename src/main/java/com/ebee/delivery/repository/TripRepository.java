package com.ebee.delivery.repository;

import com.ebee.delivery.entity.DeliveryTrip;
import com.ebee.delivery.enums.TripStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface TripRepository extends JpaRepository<DeliveryTrip, UUID> {
    List<DeliveryTrip> findByTripDate(LocalDate date);
    List<DeliveryTrip> findByDistrict(String district);
    List<DeliveryTrip> findByStatus(TripStatus status);
    List<DeliveryTrip> findByAssignedDriverId(UUID driverId);
    List<DeliveryTrip> findByTripDateAndDistrict(LocalDate date, String district);
}
