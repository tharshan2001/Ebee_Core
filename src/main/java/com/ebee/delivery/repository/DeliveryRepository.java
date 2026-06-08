package com.ebee.delivery.repository;

import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DeliveryRepository extends JpaRepository<Order, UUID> {
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.customer c LEFT JOIN FETCH c.address WHERE o.deliveryStatus = :status")
    List<Order> findByDeliveryStatus(@Param("status") DeliveryStatus status);

    List<Order> findByDeliveryAssignedToId(UUID userId);

    List<Order> findByDeliveryStatusAndDeliveryAssignedToId(DeliveryStatus status, UUID userId);

    long countByDeliveryStatus(DeliveryStatus status);
}
