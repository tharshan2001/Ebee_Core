package com.ebee.order.dto;

import com.ebee.order.enums.ReturnExchangeResolution;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record ReviewReturnExchangeRequest(
    @NotNull(message = "Approved amount is required")
    @Positive(message = "Approved amount must be positive")
    BigDecimal approvedAmount,

    @NotNull(message = "Resolution type is required")
    ReturnExchangeResolution resolutionType,

    String notes
) {}
