package com.ebee.order.dto;

import com.ebee.order.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        String chequeNumber,
        String proofFileUrl,
        String recordedByName,
        LocalDateTime createdAt
) {}
