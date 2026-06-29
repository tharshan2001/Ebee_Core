package com.ebee.expense.dto;

import com.ebee.expense.enums.ExpenseCategory;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class ExpenseRequest {

    @NotNull(message = "Category is required")
    private ExpenseCategory category;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "Description is required")
    private String description;

    @NotNull(message = "Date incurred is required")
    @com.fasterxml.jackson.annotation.JsonAlias("date")
    private LocalDate dateIncurred;

    private UUID driverId;
}
