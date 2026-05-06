package com.ebee.commission.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CommissionCalculateRequest {

    @NotNull
    private UUID orderId;
}
