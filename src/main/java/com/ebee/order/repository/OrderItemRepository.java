package com.ebee.order.repository;

import com.ebee.order.entity.OrderItem;
import com.ebee.sales.projection.CategorySalesProjection;
import com.ebee.sales.projection.ProductSalesProjection;
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

    boolean existsByComboId(UUID comboId);

    @Query("SELECT i.product.id AS productId, i.product.name AS productName, i.product.itemCode AS itemCode, i.product.category AS category, SUM(i.quantity * i.unitsPerSalesUnit) AS totalQuantity, COALESCE(SUM(i.subtotal), 0) AS totalRevenue FROM OrderItem i JOIN i.order o WHERE o.status NOT IN ('CANCELLED', 'RETURNED') AND o.createdAt >= :start AND o.createdAt < :end GROUP BY i.product.id, i.product.name, i.product.itemCode, i.product.category ORDER BY COALESCE(SUM(i.subtotal), 0) DESC")
    List<ProductSalesProjection> findSalesByProduct(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT i.product.category AS category, SUM(i.quantity * i.unitsPerSalesUnit) AS totalQuantity, COALESCE(SUM(i.subtotal), 0) AS totalRevenue FROM OrderItem i JOIN i.order o WHERE o.status NOT IN ('CANCELLED', 'RETURNED') AND o.createdAt >= :start AND o.createdAt < :end GROUP BY i.product.category")
    List<CategorySalesProjection> findSalesByCategory(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** Cost of the units actually kept by customers (returned units excluded), matching revenue's exclusions. */
    @Query("SELECT COALESCE(SUM(i.costPrice * (i.quantity - COALESCE(i.returnedQuantity, 0))), 0) FROM OrderItem i JOIN i.order o "
            + "WHERE o.status NOT IN ('CANCELLED', 'RETURNED', 'RETURN_EXCHANGE') AND o.createdAt >= :start AND o.createdAt < :end")
    java.math.BigDecimal sumCostOfGoodsSold(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** Sold lines with no recorded cost (orders created before FIFO costing) — their cost is missing from COGS. */
    @Query("SELECT COUNT(i) FROM OrderItem i JOIN i.order o WHERE i.costPrice IS NULL "
            + "AND o.status NOT IN ('CANCELLED', 'RETURNED', 'RETURN_EXCHANGE') AND o.createdAt >= :start AND o.createdAt < :end")
    long countLinesMissingCost(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
