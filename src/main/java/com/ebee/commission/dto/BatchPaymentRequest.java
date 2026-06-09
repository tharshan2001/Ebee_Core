package com.ebee.commission.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class BatchPaymentRequest {
    @NotEmpty(message = "At least one commission ID is required")
    private List<UUID> commissionIds;
}
