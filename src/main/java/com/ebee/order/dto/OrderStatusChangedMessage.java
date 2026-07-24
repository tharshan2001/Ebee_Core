package com.ebee.order.dto;

import com.ebee.order.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

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
