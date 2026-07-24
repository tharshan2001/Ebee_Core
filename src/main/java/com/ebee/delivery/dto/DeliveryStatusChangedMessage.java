package com.ebee.delivery.dto;

import com.ebee.delivery.enums.DeliveryStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable WebSocket payload broadcast over {@code /topic/deliveries} whenever
 * a delivery status changes. Consumed by the admin front-end's
 * {@code useDeliveryFeed} hook and the delivery Kanban board.
 *
 * <p>Fields are extracted from the order at event-construction time and carried
 * as plain values (no entity references). This keeps the DTO safe for
 * serialization and decoupled from Hibernate lazy-loading concerns.
 */
public record DeliveryStatusChangedMessage(
        UUID orderId,
        String orderNumber,
        DeliveryStatus previousStatus,
        DeliveryStatus newStatus,
        UUID driverId,
        String driverName,
        String customerName,
        String district,
        String reason,
        LocalDateTime occurredAt
) {}
