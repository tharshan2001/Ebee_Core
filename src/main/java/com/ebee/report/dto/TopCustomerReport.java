package com.ebee.report.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class TopCustomerReport {
    private UUID customerId;
    private String customerName;
    private String phoneNumber;
    private Long orderCount;
    private BigDecimal totalSpent;
    private BigDecimal avgOrderValue;
    private int rank;
}
