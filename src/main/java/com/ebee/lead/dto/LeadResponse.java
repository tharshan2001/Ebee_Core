package com.ebee.lead.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class LeadResponse {

    private UUID id;
    private UUID agentId;
    private String agentName;
    private String customerName;
    private String phone;
    private String email;
    private String productInterest;
    private String note;
    private LocalDateTime submittedAt;
    private UUID convertedOrderId;
}
