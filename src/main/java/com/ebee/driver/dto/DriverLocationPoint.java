package com.ebee.driver.dto;

import java.time.Instant;

public record DriverLocationPoint(
    double latitude,
    double longitude,
    Float bearing,
    Float speed,
    Float accuracy,
    Instant timestamp
) {}