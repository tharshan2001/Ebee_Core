package com.ebee.purchase.dto;

import com.ebee.purchase.enums.PurchasePaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record RecordPaymentRequest(
        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be positive")
        BigDecimal amount,

        @NotNull(message = "Payment method is required")
        PurchasePaymentMethod paymentMethod
) {}
