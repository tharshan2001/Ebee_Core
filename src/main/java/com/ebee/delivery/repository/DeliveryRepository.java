package com.ebee.delivery.repository;

import com.ebee.common.enums.District;
import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.order.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Delivery queries against the {@link Order} entity.
 * <p>
 * Although named "DeliveryRepository", the underlying entity is {@code Order} because
 * delivery attributes (status, assignee, scheduled date, photo/signature URLs, etc.)
 * are stored on the order itself rather than in a separate table. This is a deliberate
 * single-table design — see the delivery service layer for state-transition logic.
 */
@Repository
public interface DeliveryRepository extends JpaRepository<Order, UUID> {
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.customer c LEFT JOIN FETCH c.address LEFT JOIN FETCH o.trip LEFT JOIN FETCH o.deliveryAssignedTo WHERE o.deliveryStatus = :status")
    List<Order> findByDeliveryStatus(@Param("status") DeliveryStatus status);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.agent a LEFT JOIN FETCH a.user LEFT JOIN FETCH o.customer c LEFT JOIN FETCH c.address LEFT JOIN FETCH o.createdBy LEFT JOIN FETCH o.trip LEFT JOIN FETCH o.deliveryAssignedTo WHERE o.deliveryAssignedTo.id = :userId")
    List<Order> findByDeliveryAssignedToId(@Param("userId") UUID userId);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.agent a LEFT JOIN FETCH a.user LEFT JOIN FETCH o.customer c LEFT JOIN FETCH c.address LEFT JOIN FETCH o.createdBy LEFT JOIN FETCH o.trip LEFT JOIN FETCH o.deliveryAssignedTo WHERE o.deliveryAssignedTo.id = :userId AND o.deliveryStatus IN :statuses")
    List<Order> findByDeliveryAssignedToIdAndActiveStatuses(@Param("userId") UUID userId, @Param("statuses") List<DeliveryStatus> statuses);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.agent a LEFT JOIN FETCH a.user LEFT JOIN FETCH o.customer c LEFT JOIN FETCH c.address LEFT JOIN FETCH o.createdBy LEFT JOIN FETCH o.trip LEFT JOIN FETCH o.deliveryAssignedTo WHERE o.deliveryAssignedTo.id = :userId AND o.deliveryStatus IN :statuses")
    Page<Order> findByDeliveryAssignedToIdAndActiveStatusesPaged(@Param("userId") UUID userId, @Param("statuses") List<DeliveryStatus> statuses, Pageable pageable);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.agent a LEFT JOIN FETCH a.user LEFT JOIN FETCH o.customer c LEFT JOIN FETCH c.address LEFT JOIN FETCH o.createdBy LEFT JOIN FETCH o.trip LEFT JOIN FETCH o.deliveryAssignedTo WHERE o.deliveryAssignedTo.id = :userId")
    Page<Order> findByDeliveryAssignedToIdPaged(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.agent a LEFT JOIN FETCH a.user LEFT JOIN FETCH o.customer c LEFT JOIN FETCH c.address LEFT JOIN FETCH o.createdBy LEFT JOIN FETCH o.trip LEFT JOIN FETCH o.deliveryAssignedTo WHERE o.deliveryStatus = :status AND o.deliveryAssignedTo.id = :userId")
    List<Order> findByDeliveryStatusAndDeliveryAssignedToId(@Param("status") DeliveryStatus status, @Param("userId") UUID userId);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.customer c LEFT JOIN FETCH c.address LEFT JOIN FETCH o.trip LEFT JOIN FETCH o.deliveryAssignedTo WHERE o.deliveryStatus = :status AND c.address.district = :district")
    List<Order> findByDeliveryStatusAndDistrict(@Param("status") DeliveryStatus status, @Param("district") District district);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.customer c LEFT JOIN FETCH c.address LEFT JOIN FETCH o.trip LEFT JOIN FETCH o.deliveryAssignedTo WHERE o.deliveryStatus IS NOT NULL AND c.address.district = :district")
    List<Order> findByDeliveryStatusNotNullAndDistrict(@Param("district") District district);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.customer c LEFT JOIN FETCH c.address LEFT JOIN FETCH o.trip LEFT JOIN FETCH o.deliveryAssignedTo WHERE o.deliveryStatus IS NOT NULL")
    List<Order> findByDeliveryStatusNotNull();

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.customer c LEFT JOIN FETCH c.address LEFT JOIN FETCH o.trip LEFT JOIN FETCH o.deliveryAssignedTo WHERE o.trip.id = :tripId")
    List<Order> findByTripId(@Param("tripId") UUID tripId);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.customer c LEFT JOIN FETCH c.address LEFT JOIN FETCH o.trip LEFT JOIN FETCH o.deliveryAssignedTo WHERE o.trip.id = :tripId AND o.deliveryStatus = :status")
    List<Order> findByTripIdAndDeliveryStatus(@Param("tripId") UUID tripId, @Param("status") DeliveryStatus status);

    long countByDeliveryAssignedToIdAndDeliveryStatusIn(UUID userId, List<DeliveryStatus> statuses);

    long countByDeliveryStatus(DeliveryStatus status);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.customer c LEFT JOIN FETCH c.address LEFT JOIN FETCH o.deliveryAssignedTo WHERE o.deliveryStatus = :status AND o.deliveryAssignedTo IS NOT NULL AND o.updatedAt < :cutoff")
    List<Order> findStalledDeliveries(@Param("status") DeliveryStatus status, @Param("cutoff") LocalDateTime cutoff);

    @Query("SELECT o.deliveryStatus, COUNT(o) FROM Order o WHERE o.deliveryStatus IS NOT NULL AND o.updatedAt >= :start AND o.updatedAt < :end GROUP BY o.deliveryStatus")
    List<Object[]> countByDeliveryStatusAndDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT o.deliveryAssignedTo.id, CONCAT(u.firstName, ' ', u.lastName), COUNT(o), SUM(CASE WHEN o.deliveryStatus = 'DELIVERED' THEN 1 ELSE 0 END), SUM(CASE WHEN o.deliveryStatus IN ('FAILED', 'FAILED_PERMANENT') THEN 1 ELSE 0 END) FROM Order o JOIN o.deliveryAssignedTo u WHERE o.deliveryAssignedTo IS NOT NULL AND o.updatedAt >= :start AND o.updatedAt < :end GROUP BY o.deliveryAssignedTo.id, u.firstName, u.lastName")
    List<Object[]> findDeliveryStatsByDriver(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query(value = "SELECT o.delivery_failure_reason, COUNT(*) FROM orders o WHERE o.delivery_status IN ('FAILED', 'FAILED_PERMANENT') AND o.delivery_failure_reason IS NOT NULL AND o.updated_at >= :start AND o.updated_at < :end GROUP BY o.delivery_failure_reason ORDER BY COUNT(*) DESC", nativeQuery = true)
    List<Object[]> findFailureReasonBreakdown(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.customer c LEFT JOIN FETCH c.address LEFT JOIN FETCH o.trip LEFT JOIN FETCH o.deliveryAssignedTo WHERE o.deliveryStatus IS NOT NULL")
    Page<Order> findByDeliveryStatusNotNullPaged(Pageable pageable);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.customer c LEFT JOIN FETCH c.address LEFT JOIN FETCH o.trip LEFT JOIN FETCH o.deliveryAssignedTo WHERE o.deliveryStatus = :status")
    Page<Order> findByDeliveryStatusPaged(@Param("status") DeliveryStatus status, Pageable pageable);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.customer c LEFT JOIN FETCH c.address LEFT JOIN FETCH o.trip LEFT JOIN FETCH o.deliveryAssignedTo WHERE o.deliveryStatus IS NOT NULL AND c.address.district = :district")
    Page<Order> findByDeliveryStatusNotNullAndDistrictPaged(@Param("district") District district, Pageable pageable);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.customer c LEFT JOIN FETCH c.address LEFT JOIN FETCH o.trip LEFT JOIN FETCH o.deliveryAssignedTo WHERE o.deliveryStatus = :status AND c.address.district = :district")
    Page<Order> findByDeliveryStatusAndDistrictPaged(@Param("status") DeliveryStatus status, @Param("district") District district, Pageable pageable);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.customer c LEFT JOIN FETCH c.address LEFT JOIN FETCH o.trip LEFT JOIN FETCH o.deliveryAssignedTo WHERE o.trip.id = :tripId")
    Page<Order> findByTripIdPaged(@Param("tripId") UUID tripId, Pageable pageable);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.customer c LEFT JOIN FETCH c.address LEFT JOIN FETCH o.trip LEFT JOIN FETCH o.deliveryAssignedTo WHERE o.trip.id = :tripId AND o.deliveryStatus = :status")
    Page<Order> findByTripIdAndDeliveryStatusPaged(@Param("tripId") UUID tripId, @Param("status") DeliveryStatus status, Pageable pageable);
}
