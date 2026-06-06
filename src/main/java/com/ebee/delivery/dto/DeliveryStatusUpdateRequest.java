package com.ebee.delivery.dto;

import com.ebee.delivery.enums.DeliveryStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DeliveryStatusUpdateRequest {
    @NotNull
    private DeliveryStatus status;
}
