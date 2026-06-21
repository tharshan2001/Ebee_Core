package com.ebee.lead.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateLeadRequest {

    @NotNull(message = "Agent ID is required")
    private UUID agentId;

    @NotBlank(message = "Agent name is required")
    private String agentName;

    @NotBlank(message = "Customer name is required")
    private String customerName;

    @NotBlank(message = "Phone is required")
    private String phone;

    private String email;

    @NotBlank(message = "Product interest is required")
    private String productInterest;

    private String note;
}
