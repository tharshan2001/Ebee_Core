package com.ebee.purchase.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderUpdateRequest(
        @NotNull(message = "Supplier ID is required")
        UUID supplierId,

        String notes,

        @NotEmpty(message = "At least one item is required")
        @Valid
        List<PurchaseOrderItemRequest> items
) {}
