package com.ebee.trip.dto;

import com.ebee.expense.dto.ExpenseResponse;
import com.ebee.trip.enums.TripStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class TripResponse {

    private UUID id;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private TripStatus status;
    private String notes;
    private BigDecimal mileageKm;

    private UUID driverId;
    private String driverName;

    private int orderCount;
    private int paidOrderCount;
    private int partialOrderCount;
    private int unpaidOrderCount;

    private BigDecimal expectedAmount;
    private BigDecimal collectedAmount;
    private BigDecimal outstandingAmount;
    private BigDecimal netAmount;

    private BigDecimal totalExpenses;
    private BigDecimal petrolCost;
    private BigDecimal parkingCost;
    private BigDecimal tollCost;
    private BigDecimal travelCost;
    private BigDecimal maintenanceCost;
    private BigDecimal otherCost;

    private List<UUID> orderIds = new ArrayList<>();
    private List<ExpenseResponse> expenses = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
