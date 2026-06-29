package com.ebee.driver.dto;

import com.ebee.driver.enums.DriverAvailability;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class DriverDashboardResponse {
    private DriverAvailability availability;
    private long activeDeliveriesCount;
    private long completedTodayCount;
    private long failedTodayCount;
    private long unreadAlertsCount;
    private String driverName;
    private List<DriverDeliveryResponse> recentDeliveries;

    @JsonProperty("activeDeliveries")
    public long getActiveDeliveries() {
        return activeDeliveriesCount;
    }

    @JsonProperty("completedToday")
    public long getCompletedToday() {
        return completedTodayCount;
    }

    @JsonProperty("failedToday")
    public long getFailedToday() {
        return failedTodayCount;
    }

    @JsonProperty("availability")
    public boolean isAvailable() {
        return availability == DriverAvailability.AVAILABLE;
    }
}