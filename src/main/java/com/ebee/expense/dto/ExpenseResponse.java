package com.ebee.expense.dto;

import com.ebee.expense.enums.ExpenseCategory;
import com.ebee.expense.enums.ExpenseStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class ExpenseResponse {

    private UUID id;
    private ExpenseCategory category;
    private BigDecimal amount;
    private String description;
    private String receiptUrl;
    private LocalDate dateIncurred;
    private ExpenseStatus status;

    @JsonProperty("date")
    public LocalDate getDate() {
        return dateIncurred;
    }

    private UUID createdById;
    private String createdByEmail;
    private String createdByName;

    private UUID driverId;
    private String driverName;

    private UUID approvedById;
    private String approvedByName;
    private LocalDateTime approvedAt;
    private String rejectionReason;

    private LocalDateTime paidAt;
    private String paymentReference;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
