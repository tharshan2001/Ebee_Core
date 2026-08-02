package com.ebee.customer.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record OutstandingAdjustmentResponse(
        UUID customerId,
        BigDecimal previousOutstanding,
        BigDecimal newOutstanding,
        BigDecimal amountAdded,
        LocalDate effectiveDate,
        String description
) {}
