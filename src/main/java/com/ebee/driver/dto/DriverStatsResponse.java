package com.ebee.driver.dto;

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
}
