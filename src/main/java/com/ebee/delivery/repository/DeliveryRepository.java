package com.ebee.delivery.repository;

import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface DeliveryRepository extends JpaRepository<Order, UUID> {
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.customer c LEFT JOIN FETCH c.address WHERE o.deliveryStatus = :status")
    List<Order> findByDeliveryStatus(@Param("status") DeliveryStatus status);

    List<Order> findByDeliveryAssignedToId(UUID userId);

    List<Order> findByDeliveryStatusAndDeliveryAssignedToId(DeliveryStatus status, UUID userId);

    long countByDeliveryAssignedToIdAndDeliveryStatusIn(UUID userId, List<DeliveryStatus> statuses);

    long countByDeliveryStatus(DeliveryStatus status);

    @Query("SELECT o.deliveryStatus, COUNT(o) FROM Order o WHERE o.deliveryStatus IS NOT NULL AND o.updatedAt >= :start AND o.updatedAt < :end GROUP BY o.deliveryStatus")
    List<Object[]> countByDeliveryStatusAndDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT o.deliveryAssignedTo.id, CONCAT(u.firstName, ' ', u.lastName), COUNT(o), SUM(CASE WHEN o.deliveryStatus = 'DELIVERED' THEN 1 ELSE 0 END), SUM(CASE WHEN o.deliveryStatus IN ('FAILED', 'FAILED_PERMANENT') THEN 1 ELSE 0 END) FROM Order o JOIN o.deliveryAssignedTo u WHERE o.deliveryAssignedTo IS NOT NULL AND o.updatedAt >= :start AND o.updatedAt < :end GROUP BY o.deliveryAssignedTo.id, u.firstName, u.lastName")
    List<Object[]> findDeliveryStatsByDriver(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query(value = "SELECT o.delivery_failure_reason, COUNT(*) FROM orders o WHERE o.delivery_status IN ('FAILED', 'FAILED_PERMANENT') AND o.delivery_failure_reason IS NOT NULL AND o.updated_at >= :start AND o.updated_at < :end GROUP BY o.delivery_failure_reason ORDER BY COUNT(*) DESC", nativeQuery = true)
    List<Object[]> findFailureReasonBreakdown(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
