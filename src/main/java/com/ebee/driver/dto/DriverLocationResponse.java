package com.ebee.driver.dto;

import java.time.Instant;
import java.util.UUID;

public record DriverLocationResponse(
    UUID driverId,
    String driverName,
    double latitude,
    double longitude,
    Float accuracy,
    Float bearing,
    Float speed,
    Instant recordedAt
) {}
