package com.ebee.delivery.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AssignDriverRequest {
    @NotNull
    private UUID driverId;
}
