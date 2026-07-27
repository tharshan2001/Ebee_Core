package com.ebee.customer.dto;

import com.ebee.customer.enums.AdvanceReferenceType;
import com.ebee.customer.enums.AdvanceTransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record AdvanceTransactionResponse(
        UUID id,
        UUID customerId,
        UUID orderId,
        BigDecimal amount,
        BigDecimal balanceAfter,
        AdvanceTransactionType transactionType,
        AdvanceReferenceType referenceType,
        UUID referenceId,
        String description,
        LocalDateTime createdAt
) {}
