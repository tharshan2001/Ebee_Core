package com.ebee.order.repository;

import com.ebee.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.agent a LEFT JOIN FETCH a.user LEFT JOIN FETCH o.customer LEFT JOIN FETCH o.createdBy LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product WHERE o.id = :id")
    Optional<Order> findByIdWithDetails(@Param("id") UUID id);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.agent a LEFT JOIN FETCH a.user LEFT JOIN FETCH o.customer LEFT JOIN FETCH o.createdBy LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product WHERE o.orderNumber = :orderNumber")
    Optional<Order> findByOrderNumberWithDetails(@Param("orderNumber") String orderNumber);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.agent a LEFT JOIN FETCH a.user LEFT JOIN FETCH o.customer LEFT JOIN FETCH o.createdBy LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product WHERE a.id = :agentId")
    List<Order> findByAgentIdWithDetails(@Param("agentId") UUID agentId);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.agent a LEFT JOIN FETCH a.user LEFT JOIN FETCH o.customer LEFT JOIN FETCH o.createdBy LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product WHERE o.customer.id = :customerId")
    List<Order> findByCustomerIdWithDetails(@Param("customerId") UUID customerId);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.agent a LEFT JOIN FETCH a.user LEFT JOIN FETCH o.customer LEFT JOIN FETCH o.createdBy LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product")
    List<Order> findAllWithDetails();

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.agent a LEFT JOIN FETCH a.user LEFT JOIN FETCH o.customer LEFT JOIN FETCH o.createdBy LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product WHERE o.trip.id = :tripId")
    List<Order> findByTripIdWithDetails(@Param("tripId") UUID tripId);

    Optional<Order> findByOrderNumber(String orderNumber);

    List<Order> findByAgentId(UUID agentId);

    List<Order> findByCreatedById(UUID userId);

    List<Order> findByStatus(com.ebee.order.enums.OrderStatus status);

    boolean existsByOrderNumber(String orderNumber);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status NOT IN ('CANCELLED', 'RETURNED') AND o.createdAt >= :start AND o.createdAt < :end")
    BigDecimal sumRevenueByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.status NOT IN ('CANCELLED', 'RETURNED') AND o.createdAt >= :start AND o.createdAt < :end")
    Long countOrdersByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT a.id, a.agentCode, a.user.firstName, a.user.lastName, COUNT(o), COALESCE(SUM(o.totalAmount), 0) FROM Order o LEFT JOIN o.agent a LEFT JOIN a.user WHERE o.status NOT IN ('CANCELLED', 'RETURNED') AND o.createdAt >= :start AND o.createdAt < :end GROUP BY a.id, a.agentCode, a.user.firstName, a.user.lastName")
    List<Object[]> findSalesByAgent(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0), COUNT(o) FROM Order o WHERE o.agent.id = :agentId AND o.status NOT IN ('CANCELLED', 'RETURNED') AND o.createdAt >= :start AND o.createdAt < :end")
    List<Object[]> findSalesSummaryByAgent(@Param("agentId") UUID agentId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query(value = "SELECT DATE_TRUNC('day', o.created_at) AS period, COUNT(*) AS orderCount, COALESCE(SUM(o.total_amount), 0) AS revenue FROM orders o WHERE o.status NOT IN ('CANCELLED', 'RETURNED') AND o.created_at >= :start AND o.created_at < :end GROUP BY DATE_TRUNC('day', o.created_at) ORDER BY period", nativeQuery = true)
    List<Object[]> findDailyTrend(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query(value = "SELECT DATE_TRUNC('month', o.created_at) AS period, COUNT(*) AS orderCount, COALESCE(SUM(o.total_amount), 0) AS revenue FROM orders o WHERE o.status NOT IN ('CANCELLED', 'RETURNED') AND o.created_at >= :start AND o.created_at < :end GROUP BY DATE_TRUNC('month', o.created_at) ORDER BY period", nativeQuery = true)
    List<Object[]> findMonthlyTrend(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query(value = "SELECT DATE_TRUNC('year', o.created_at) AS period, COUNT(*) AS orderCount, COALESCE(SUM(o.total_amount), 0) AS revenue FROM orders o WHERE o.status NOT IN ('CANCELLED', 'RETURNED') AND o.created_at >= :start AND o.created_at < :end GROUP BY DATE_TRUNC('year', o.created_at) ORDER BY period", nativeQuery = true)
    List<Object[]> findYearlyTrend(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.createdAt >= :start AND o.createdAt < :end")
    Long countOrdersByDateRangeAllStatuses(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = 'DELIVERED' AND o.deliveredAt >= :start AND o.deliveredAt < :end")
    Long countDeliveredByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

}
