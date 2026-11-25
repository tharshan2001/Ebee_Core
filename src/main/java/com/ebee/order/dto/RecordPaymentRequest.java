package com.ebee.order.dto;

import com.ebee.order.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record RecordPaymentRequest(
        @NotNull(message = "Payment amount is required")
        @Positive(message = "Payment amount must be positive")
        BigDecimal amount,

        @NotNull(message = "Payment method is required")
        PaymentMethod paymentMethod,

        String chequeNumber,

        String proofFileUrl,

        /** Bank/cash account the money went into; null = default account for the payment method. */
        java.util.UUID accountId
) {
    public RecordPaymentRequest(BigDecimal amount, PaymentMethod paymentMethod, String chequeNumber, String proofFileUrl) {
        this(amount, paymentMethod, chequeNumber, proofFileUrl, null);
    }
}
