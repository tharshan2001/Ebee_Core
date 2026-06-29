package com.ebee.driver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class DriverStatsResponse {
    private long totalDeliveriesCompleted;
    private long totalDeliveriesFailed;
    private long deliveriesThisWeek;
    private long deliveriesThisMonth;
    private BigDecimal averageDeliveryTimeHours;
    private BigDecimal totalExpensesClaimed;
    private long totalExpensesCount;

    @JsonProperty("totalDeliveries")
    public long getTotalDeliveries() {
        return totalDeliveriesCompleted + totalDeliveriesFailed;
    }

    @JsonProperty("completedDeliveries")
    public long getCompletedDeliveries() {
        return totalDeliveriesCompleted;
    }

    @JsonProperty("failedDeliveries")
    public long getFailedDeliveries() {
        return totalDeliveriesFailed;
    }

    @JsonProperty("totalEarnings")
    public BigDecimal getTotalEarnings() {
        return BigDecimal.ZERO;
    }

    @JsonProperty("rating")
    public Double getRating() {
        return null;
    }
}