package com.ebee.driver.dto;

import jakarta.validation.constraints.NotNull;

public record DriverLocationRequest(
    @NotNull Double latitude,
    @NotNull Double longitude,
    Float accuracy,
    Float bearing,
    Float speed
) {}
