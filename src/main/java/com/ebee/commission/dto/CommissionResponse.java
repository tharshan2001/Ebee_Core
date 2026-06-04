package com.ebee.commission.dto;

import com.ebee.commission.enums.CommissionStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class CommissionResponse {

    private UUID id;
    private UUID agentId;
    private String agentCode;
    private String agentName;
    private BigDecimal agentTotalSales;
    private UUID orderId;
    private String orderNumber;
    private BigDecimal orderAmount;
    private BigDecimal commissionRate;
    private BigDecimal commissionAmount;
    private CommissionStatus status;
    private LocalDateTime calculatedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime paidAt;
    private String notes;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
