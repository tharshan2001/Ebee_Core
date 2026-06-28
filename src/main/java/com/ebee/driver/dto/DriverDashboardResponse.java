package com.ebee.driver.dto;

import com.ebee.driver.enums.DriverAvailability;
import lombok.Data;

import java.util.List;

@Data
public class DriverDashboardResponse {
    private DriverAvailability availability;
    private long activeDeliveriesCount;
    private long completedTodayCount;
    private long failedTodayCount;
    private long unreadAlertsCount;
    private List<DriverDeliveryResponse> recentDeliveries;
}
