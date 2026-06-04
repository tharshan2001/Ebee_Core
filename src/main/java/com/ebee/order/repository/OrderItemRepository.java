package com.ebee.order.repository;

import com.ebee.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    List<OrderItem> findByOrderId(UUID orderId);

    List<OrderItem> findByProductId(UUID productId);

    @Query("SELECT i.product.id, i.product.name, i.product.itemCode, i.product.category, SUM(i.quantity), COALESCE(SUM(i.subtotal), 0) FROM OrderItem i JOIN i.order o WHERE o.status NOT IN ('CANCELLED', 'RETURNED') AND o.createdAt >= :start AND o.createdAt < :end GROUP BY i.product.id, i.product.name, i.product.itemCode, i.product.category ORDER BY COALESCE(SUM(i.subtotal), 0) DESC")
    List<Object[]> findSalesByProduct(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT i.product.category, SUM(i.quantity), COALESCE(SUM(i.subtotal), 0) FROM OrderItem i JOIN i.order o WHERE o.status NOT IN ('CANCELLED', 'RETURNED') AND o.createdAt >= :start AND o.createdAt < :end GROUP BY i.product.category")
    List<Object[]> findSalesByCategory(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
