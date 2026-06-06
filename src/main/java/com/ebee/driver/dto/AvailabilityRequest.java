package com.ebee.driver.dto;

import com.ebee.driver.enums.DriverAvailability;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AvailabilityRequest {
    @NotNull
    private DriverAvailability availability;
}
