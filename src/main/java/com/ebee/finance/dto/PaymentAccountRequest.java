package com.ebee.finance.dto;

import com.ebee.finance.entity.PaymentAccount;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/** openingBalance/openingDate are only used on create. */
public record PaymentAccountRequest(
        @NotBlank String name,
        @NotNull PaymentAccount.Type type,
        String bankName,
        String accountNumber,
        BigDecimal openingBalance,
        LocalDate openingDate,
        Boolean active) {
}
