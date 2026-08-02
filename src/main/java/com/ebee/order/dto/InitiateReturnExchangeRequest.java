package com.ebee.order.dto;

import com.ebee.order.enums.ReturnExchangeResolution;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record InitiateReturnExchangeRequest(
    @NotNull(message = "Requested amount is required")
    @Positive(message = "Requested amount must be positive")
    BigDecimal requestedAmount,

    @NotBlank(message = "Reason is required")
    String reason
) {}
