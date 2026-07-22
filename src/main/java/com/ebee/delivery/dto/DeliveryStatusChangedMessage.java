package com.ebee.delivery.dto;

import com.ebee.delivery.enums.DeliveryStatus;

import java.time.LocalDateTime;
import java.util.UUID;

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
