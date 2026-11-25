package com.ebee.commission.dto;

import com.ebee.commission.enums.CommissionType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class CommissionConfigResponse {

    private UUID id;
    private String name;
    private java.math.BigDecimal commissionRate;
    private CommissionType type;
    private java.math.BigDecimal minAmount;
    private java.math.BigDecimal maxAmount;
    private String appliesTo;
    private Boolean isActive;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
