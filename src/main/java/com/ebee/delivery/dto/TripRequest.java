package com.ebee.delivery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class TripRequest {
    @NotNull
    private LocalDate tripDate;

    @NotBlank
    private String district;

    private UUID driverId;

    private String notes;
}
