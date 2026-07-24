package com.ebee.order.dto;

import com.ebee.order.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable WebSocket payload broadcast over {@code /topic/orders} whenever an
 * order is created, its status changes, or it is cancelled/returned.
 *
 * <p>Serialized to JSON by {@link org.springframework.messaging.simp.SimpMessagingTemplate}
 * and consumed by the admin front-end's {@code useOrderFeed} hook. The
 * {@code created} flag lets the client distinguish a new order from a status
 * update so it can either prepend the row or update the existing one.
 *
 * <p>All fields are read-only (record). The listener constructs an instance
 * directly from {@link com.ebee.order.event.OrderStatusChangedEvent} — no
 * mapping layer is used since the DTO mirrors the event fields.
 */
public record OrderStatusChangedMessage(
        UUID orderId,
        String orderNumber,
        OrderStatus previousStatus,
        OrderStatus newStatus,
        String agentName,
        String customerName,
        String customerPhone,
        BigDecimal total,
        BigDecimal paidAmount,
        LocalDateTime occurredAt,
        boolean created
) {}
