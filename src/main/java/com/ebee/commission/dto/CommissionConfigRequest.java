package com.ebee.commission.dto;

import com.ebee.commission.enums.CommissionType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CommissionConfigRequest {

    @NotNull
    private String name;

    @NotNull
    private Double commissionRate;

    private CommissionType type;

    private Double minAmount;

    private Double maxAmount;

    @NotNull
    private String appliesTo;

    private Boolean isActive;

    private java.time.LocalDateTime effectiveFrom;

    private java.time.LocalDateTime effectiveTo;
}
