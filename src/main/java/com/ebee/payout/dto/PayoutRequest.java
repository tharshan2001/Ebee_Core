package com.ebee.payout.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class PayoutRequest {

    @NotEmpty(message = "Agent IDs are required")
    private List<UUID> agentIds;
}
