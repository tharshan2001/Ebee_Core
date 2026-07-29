package com.ebee.accounting.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record RecordLedgerRequest(
    @NotNull String transactionType,
    @NotNull @Positive BigDecimal amount,
    String description
) {}
