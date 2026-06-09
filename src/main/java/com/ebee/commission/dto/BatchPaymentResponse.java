package com.ebee.commission.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class BatchPaymentResponse {
    private BigDecimal totalPaid;
    private int commissionsPaid;
    private BigDecimal remainingAvailable;
    private List<CommissionResponse> paidCommissions;
}
