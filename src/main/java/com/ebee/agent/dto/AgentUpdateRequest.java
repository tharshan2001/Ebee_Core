package com.ebee.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AgentUpdateRequest {

    @NotBlank(message = "Agent code is required")
    private String agentCode;

    private String phoneNumber;

    @NotNull(message = "Commission rate is required")
    private Double commissionRate;
}
