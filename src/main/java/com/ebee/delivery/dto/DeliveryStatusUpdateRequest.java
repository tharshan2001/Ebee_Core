package com.ebee.delivery.dto;

import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.order.enums.RefundChoice;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DeliveryStatusUpdateRequest {
    @NotNull
    private DeliveryStatus status;

    private String reason;

    /**
     * Required for FAILED_PERMANENT when the customer has already paid something:
     * REFUND (cash/bank) or CREDIT_ADVANCE (into their advance balance).
     */
    private RefundChoice refundChoice;
}
