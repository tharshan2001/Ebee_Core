package com.ebee.customer.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record CreditLimitRequest(
        @NotNull(message = "Credit limit is required")
        @PositiveOrZero(message = "Credit limit must be zero or positive")
        BigDecimal creditLimit
) {}
