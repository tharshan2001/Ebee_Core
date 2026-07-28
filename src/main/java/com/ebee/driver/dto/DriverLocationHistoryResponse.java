package com.ebee.driver.dto;

import java.util.List;
import java.util.UUID;

public record DriverLocationHistoryResponse(
    UUID driverId,
    String driverName,
    String photoUrl,
    List<DriverLocationPoint> points
) {}