package com.ebee.finance.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Transfer / cheque deposit: fromAccountId → toAccountId. Owner deposit/withdrawal: accountId only (fromAccountId). */
public record MoneyMoveRequest(
        @NotNull UUID fromAccountId,
        UUID toAccountId,
        @NotNull @Positive BigDecimal amount,
        LocalDate date,
        String note) {
}
