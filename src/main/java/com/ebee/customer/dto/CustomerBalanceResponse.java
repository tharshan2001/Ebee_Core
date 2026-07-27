package com.ebee.customer.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CustomerBalanceResponse(
        UUID customerId,
        String customerName,
        BigDecimal outstandingBalance,
        BigDecimal advanceBalance
) {}
