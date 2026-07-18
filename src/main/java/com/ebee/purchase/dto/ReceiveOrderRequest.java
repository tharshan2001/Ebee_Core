package com.ebee.purchase.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ReceiveOrderRequest(
        @NotEmpty List<@Valid ItemReceipt> items
) {
    public record ItemReceipt(
            @NotNull UUID itemId,
            @Min(1) int receivedQuantity
    ) {}
}
