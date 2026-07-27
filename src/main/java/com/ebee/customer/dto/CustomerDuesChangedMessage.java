package com.ebee.customer.dto;

import com.ebee.customer.enums.DuesChangeType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CustomerDuesChangedMessage(
        UUID customerId,
        String customerName,
        BigDecimal previousOutstanding,
        BigDecimal newOutstanding,
        BigDecimal previousAdvance,
        BigDecimal newAdvance,
        BigDecimal outstandingChange,
        BigDecimal advanceChange,
        DuesChangeType changeType,
        LocalDateTime occurredAt
) {}
