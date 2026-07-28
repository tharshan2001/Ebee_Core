package com.ebee.driver.dto;

import java.time.Instant;
import java.util.UUID;

public record DriverLocationResponse(
    UUID driverId,
    String driverName,
    String photoUrl,
    double latitude,
    double longitude,
    Float bearing,
    Float speed,
    Float accuracy,
    Instant recordedAt,
    long seq
) {}