package com.ebee.finance.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/** An account's balance before the system started recording its movements. Negative = overdrawn. */
public record OpeningBalanceRequest(@NotNull BigDecimal amount, LocalDate date) {
}
