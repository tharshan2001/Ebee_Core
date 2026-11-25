package com.ebee.order.repository;

import com.ebee.order.entity.OrderItemLot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OrderItemLotRepository extends JpaRepository<OrderItemLot, UUID> {

    /** Lots taken by an order line, most recently taken last. */
    List<OrderItemLot> findByOrderItemIdOrderByCreatedAtAsc(UUID orderItemId);

    /** Recall lookup: every sale of a product's lot. */
    @Query("SELECT l FROM OrderItemLot l JOIN FETCH l.orderItem i JOIN FETCH i.order o "
            + "WHERE l.productId = :productId AND l.lotNumber = :lotNumber ORDER BY o.createdAt DESC")
    List<OrderItemLot> findSalesOfLot(@Param("productId") UUID productId, @Param("lotNumber") String lotNumber);
}
