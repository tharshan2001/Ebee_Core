package com.ebee.trip.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class UpdateTripRequest {

    private String name;

    private UUID driverId;

    private LocalDate startDate;

    private LocalDate endDate;

    private String notes;

    private BigDecimal mileageKm;
}
