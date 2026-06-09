package com.ebee.commission.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class PendingPaymentSummary {
    private BigDecimal totalAvailable;
    private int processingCount;
    private List<CommissionResponse> commissions;
}
