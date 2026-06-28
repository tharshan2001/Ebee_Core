package com.ebee.driver.dto;

import com.ebee.driver.enums.DriverAvailability;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityRequest {
    @NotNull
    private DriverAvailability availability;
}
