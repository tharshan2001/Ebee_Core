package com.ebee.order.dto;

import com.ebee.order.enums.RefundChoice;
import jakarta.validation.constraints.NotNull;

public record OrderSettlementRequest(
        @NotNull(message = "Refund choice is required")
        RefundChoice refundChoice,

        String notes
) {}
