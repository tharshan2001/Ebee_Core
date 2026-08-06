package com.ebee.order.dto;

import com.ebee.order.enums.RefundChoice;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReturnOrderItemRequest(
    List<ReturnItemRequest> items,

    @NotNull(message = "Refund choice is required")
    RefundChoice refundChoice,

    String notes
) {}
