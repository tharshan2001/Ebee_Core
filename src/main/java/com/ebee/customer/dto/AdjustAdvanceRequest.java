package com.ebee.customer.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record AdjustAdvanceRequest(
        @NotNull(message = "Amount is required")
        BigDecimal amount,

        @NotNull(message = "Description is required")
        String description
) {}
