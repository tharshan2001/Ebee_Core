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
    /** Lot fields are optional, except expiryDate for products that track expiry. */
    public record ItemReceipt(
            @NotNull UUID itemId,
            @Min(1) int receivedQuantity,
            String lotNumber,
            java.time.LocalDate expiryDate,
            java.time.LocalDate manufacturedDate
    ) {
        public ItemReceipt(UUID itemId, int receivedQuantity) {
            this(itemId, receivedQuantity, null, null, null);
        }
    }
}
