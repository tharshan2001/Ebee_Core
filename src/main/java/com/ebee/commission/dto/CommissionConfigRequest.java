package com.ebee.commission.dto;

import com.ebee.commission.enums.CommissionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CommissionConfigRequest {

    @NotNull
    private String name;

    @NotNull
    @Positive(message = "Commission rate must be positive")
    private Double commissionRate;

    private CommissionType type;

    @Positive(message = "Min amount must be positive")
    private Double minAmount;

    @Positive(message = "Max amount must be positive")
    private Double maxAmount;

    @NotNull
    private String appliesTo;

    private Boolean isActive;

    private java.time.LocalDateTime effectiveFrom;

    private java.time.LocalDateTime effectiveTo;
}
