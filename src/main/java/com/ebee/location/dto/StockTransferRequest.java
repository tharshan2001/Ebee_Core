package com.ebee.location.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.UUID;

public record StockTransferRequest(
        @NotNull UUID fromLocationId,
        @NotNull UUID toLocationId,
        @NotEmpty @Valid List<Item> items,
        String notes) {

    public record Item(@NotNull UUID productId, @NotNull @Positive Integer quantity) {
    }
}
