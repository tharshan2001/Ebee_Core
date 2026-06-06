package com.ebee.delivery.repository;

import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DeliveryRepository extends JpaRepository<Order, UUID> {
    List<Order> findByDeliveryStatus(DeliveryStatus status);
    List<Order> findByDeliveryAssignedToId(UUID userId);
    List<Order> findByDistrict(String district);
    List<Order> findByTripId(UUID tripId);
    List<Order> findByDeliveryStatusAndDeliveryAssignedToId(DeliveryStatus status, UUID userId);
    long countByDeliveryStatus(DeliveryStatus status);
}
