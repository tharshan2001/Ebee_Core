package com.ebee.order.repository;

import com.ebee.order.entity.Order;
import com.ebee.order.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ebee.sales.projection.AgentSalesProjection;
import com.ebee.sales.projection.CategorySalesProjection;
import com.ebee.sales.projection.ProductSalesProjection;
import com.ebee.sales.projection.SalesSummaryProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.agent a LEFT JOIN FETCH a.user LEFT JOIN FETCH o.customer LEFT JOIN FETCH o.createdBy LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product WHERE o.id = :id")
    Optional<Order> findByIdWithDetails(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.agent a LEFT JOIN FETCH a.user LEFT JOIN FETCH o.customer c LEFT JOIN FETCH c.address LEFT JOIN FETCH o.createdBy WHERE o.id = :id")
    Optional<Order> findByIdForUpdateWithDetails(@Param("id") UUID id);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.agent a LEFT JOIN FETCH a.user LEFT JOIN FETCH o.customer LEFT JOIN FETCH o.createdBy LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product WHERE o.orderNumber = :orderNumber")
    Optional<Order> findByOrderNumberWithDetails(@Param("orderNumber") String orderNumber);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.agent a LEFT JOIN FETCH a.user LEFT JOIN FETCH o.customer LEFT JOIN FETCH o.createdBy LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product WHERE a.id = :agentId")
    List<Order> findByAgentIdWithDetails(@Param("agentId") UUID agentId);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.agent a LEFT JOIN FETCH a.user LEFT JOIN FETCH o.customer LEFT JOIN FETCH o.createdBy LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product WHERE o.customer.id = :customerId")
    List<Order> findByCustomerIdWithDetails(@Param("customerId") UUID customerId);

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.agent a LEFT JOIN FETCH a.user LEFT JOIN FETCH o.customer LEFT JOIN FETCH o.createdBy LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product")
    List<Order> findAllWithDetails();

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.agent a LEFT JOIN FETCH a.user LEFT JOIN FETCH o.customer LEFT JOIN FETCH o.createdBy")
    Page<Order> findAllWithDetails(Pageable pageable);

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.agent a LEFT JOIN FETCH a.user LEFT JOIN FETCH o.customer LEFT JOIN FETCH o.createdBy WHERE o.status = :status")
    Page<Order> findByStatusWithDetails(@Param("status") com.ebee.order.enums.OrderStatus status, Pageable pageable);

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.agent a LEFT JOIN FETCH a.user LEFT JOIN FETCH o.customer LEFT JOIN FETCH o.createdBy WHERE o.agent.id = :agentId")
    Page<Order> findByAgentIdWithDetails(@Param("agentId") UUID agentId, Pageable pageable);

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.agent a LEFT JOIN FETCH a.user LEFT JOIN FETCH o.customer LEFT JOIN FETCH o.createdBy WHERE o.customer.id = :customerId")
    Page<Order> findByCustomerIdWithDetails(@Param("customerId") UUID customerId, Pageable pageable);

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.agent a LEFT JOIN FETCH a.user LEFT JOIN FETCH o.createdBy JOIN FETCH o.items i JOIN FETCH i.product WHERE o.customer.id = :customerId AND o.paymentStatus IN :statuses ORDER BY o.createdAt ASC")
    List<Order> findByCustomerIdAndPaymentStatusInWithDetails(@Param("customerId") UUID customerId, @Param("statuses") List<PaymentStatus> statuses);

    Optional<Order> findByOrderNumber(String orderNumber);

    List<Order> findByAgentId(UUID agentId);

    List<Order> findByCreatedById(UUID userId);

    List<Order> findByStatus(com.ebee.order.enums.OrderStatus status);

    boolean existsByOrderNumber(String orderNumber);

    @Query("SELECT MAX(o.orderNumber) FROM Order o WHERE o.orderNumber LIKE :pattern")
    String findMaxOrderNumberByPattern(@Param("pattern") String pattern);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status NOT IN ('CANCELLED', 'RETURNED') AND o.createdAt >= :start AND o.createdAt < :end")
    BigDecimal sumRevenueByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(o.paidAmount), 0) FROM Order o WHERE o.status NOT IN ('CANCELLED', 'RETURNED') AND o.createdAt >= :start AND o.createdAt < :end")
    BigDecimal sumCollectedRevenueByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(o.outstandingBalance), 0) FROM Order o WHERE o.paymentStatus IN ('UNPAID', 'PARTIALLY_PAID') AND o.createdAt >= :start AND o.createdAt < :end")
    BigDecimal sumOutstandingByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(o.outstandingBalance), 0) FROM Order o WHERE o.customer.id = :customerId AND o.paymentStatus IN ('UNPAID', 'PARTIALLY_PAID')")
    BigDecimal sumOutstandingByCustomerId(@Param("customerId") UUID customerId);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.status NOT IN ('CANCELLED', 'RETURNED') AND o.createdAt >= :start AND o.createdAt < :end")
    Long countOrdersByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT a.id AS agentId, a.agentCode AS agentCode, a.user.firstName AS firstName, a.user.lastName AS lastName, COUNT(o) AS totalOrders, COALESCE(SUM(o.totalAmount), 0) AS totalRevenue FROM Order o LEFT JOIN o.agent a LEFT JOIN a.user WHERE o.status NOT IN ('CANCELLED', 'RETURNED') AND o.createdAt >= :start AND o.createdAt < :end GROUP BY a.id, a.agentCode, a.user.firstName, a.user.lastName")
    List<AgentSalesProjection> findSalesByAgent(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) AS totalRevenue, COUNT(o) AS totalOrders FROM Order o WHERE o.agent.id = :agentId AND o.status NOT IN ('CANCELLED', 'RETURNED') AND o.createdAt >= :start AND o.createdAt < :end")
    List<SalesSummaryProjection> findSalesSummaryByAgent(@Param("agentId") UUID agentId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

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

    @Query("SELECT o.status, COUNT(o), COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.createdAt >= :start AND o.createdAt < :end GROUP BY o.status")
    List<Object[]> findOrderStatusDistribution(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT o.orderSource, COUNT(o), COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status NOT IN ('CANCELLED', 'RETURNED') AND o.createdAt >= :start AND o.createdAt < :end GROUP BY o.orderSource")
    List<Object[]> findSalesBySource(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT c.id, CONCAT(c.firstName, ' ', c.lastName), c.phoneNumber, COUNT(o), COALESCE(SUM(o.totalAmount), 0) FROM Order o JOIN o.customer c WHERE o.status NOT IN ('CANCELLED', 'RETURNED') AND o.createdAt >= :start AND o.createdAt < :end GROUP BY c.id, c.firstName, c.lastName, c.phoneNumber ORDER BY COALESCE(SUM(o.totalAmount), 0) DESC")
    List<Object[]> findTopCustomersByRevenue(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query(value = "SELECT AVG(EXTRACT(EPOCH FROM (o.delivered_at - o.created_at)) / 3600), MIN(EXTRACT(EPOCH FROM (o.delivered_at - o.created_at)) / 3600), MAX(EXTRACT(EPOCH FROM (o.delivered_at - o.created_at)) / 3600), COUNT(o) FROM orders o WHERE o.status = 'DELIVERED' AND o.delivered_at >= :start AND o.delivered_at < :end", nativeQuery = true)
    List<Object[]> findAvgDeliveryTime(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.createdAt >= :start AND o.createdAt < :end")
    Long countTotalOrdersByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<Order> findByTripId(UUID tripId);

    @Query("SELECT o FROM Order o WHERE o.trip IS NULL AND o.status = :status")
    List<Order> findByTripIsNullAndStatus(@Param("status") com.ebee.order.enums.OrderStatus status);

    @Query("SELECT o FROM Order o WHERE o.trip IS NULL")
    List<Order> findByTripIsNull();
}
