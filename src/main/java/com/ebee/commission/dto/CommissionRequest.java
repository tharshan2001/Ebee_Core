package com.ebee.commission.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CommissionRequest {

    @NotNull
    private UUID agentId;

    @NotNull
    private UUID orderId;

    private UUID configId;

    private String notes;
}
