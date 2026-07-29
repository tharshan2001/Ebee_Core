package com.ebee.accounting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PeriodActionRequest(
    @NotNull UUID periodId,
    @NotBlank String reason
) {}
