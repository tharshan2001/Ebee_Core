package com.ebee.purchase.dto;

import jakarta.validation.constraints.NotNull;
import com.ebee.purchase.enums.PurchaseOrderStatus;

public record PurchaseOrderStatusRequest(
        @NotNull(message = "Status is required")
        PurchaseOrderStatus status
) {}
