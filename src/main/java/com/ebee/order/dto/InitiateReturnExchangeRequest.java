package com.ebee.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record InitiateReturnExchangeRequest(
    @NotEmpty(message = "At least one item is required")
    @Valid
    List<ReturnItemRequest> items,

    @NotBlank(message = "Reason is required")
    String reason
) {}
