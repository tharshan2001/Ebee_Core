package com.ebee.order.dto;

import com.ebee.order.enums.ReturnExchangeResolution;
import com.ebee.order.enums.ReturnExchangeStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ReturnExchangeResponse(
    UUID id,
    UUID orderId,
    String orderNumber,
    UUID customerId,
    String customerName,
    BigDecimal requestedAmount,
    BigDecimal approvedAmount,
    ReturnExchangeStatus status,
    ReturnExchangeResolution resolutionType,
    String reason,
    String notes,
    String reviewedByName,
    LocalDateTime reviewedAt,
    LocalDateTime createdAt
) {}
