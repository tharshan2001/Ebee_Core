package com.ebee.driver.dto;

import com.ebee.delivery.enums.DeliveryStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DriverDeliveryStatusRequest {
    @NotNull
    private DeliveryStatus status;
    private String reason;
    private String driverNotes;
}
