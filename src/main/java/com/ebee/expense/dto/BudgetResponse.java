package com.ebee.expense.dto;

import com.ebee.expense.enums.ExpenseCategory;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class BudgetResponse {

    private UUID id;
    private ExpenseCategory category;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal allocatedAmount;
    private BigDecimal spentAmount;
    private BigDecimal remainingAmount;
    private BigDecimal utilizationPercent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public BigDecimal getRemainingAmount() {
        if (allocatedAmount == null) return BigDecimal.ZERO;
        if (spentAmount == null) return allocatedAmount;
        return allocatedAmount.subtract(spentAmount);
    }

    public BigDecimal getUtilizationPercent() {
        if (allocatedAmount == null || allocatedAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        if (spentAmount == null) {
            return BigDecimal.ZERO;
        }
        return spentAmount.multiply(BigDecimal.valueOf(100))
                .divide(allocatedAmount, 1, RoundingMode.HALF_UP);
    }
}
