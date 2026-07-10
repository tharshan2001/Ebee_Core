package com.ebee.purchase.dto;

import com.ebee.purchase.enums.PurchasePaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderRequest(
        @NotNull(message = "Supplier ID is required")
        UUID supplierId,

        String notes,

        @NotEmpty(message = "At least one item is required")
        @Valid
        List<PurchaseOrderItemRequest> items,

        PurchasePaymentMethod paymentMethod,

        BigDecimal paidAmount
) {}
