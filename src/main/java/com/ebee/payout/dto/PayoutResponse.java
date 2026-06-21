package com.ebee.payout.dto;

import com.ebee.payout.enums.PayoutStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class PayoutResponse {

    private UUID id;
    private UUID agentId;
    private String agentName;
    private BigDecimal totalAmount;
    private List<UUID> commissionIds;
    private PayoutStatus status;
    private LocalDateTime paidAt;
    private String pdfUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
