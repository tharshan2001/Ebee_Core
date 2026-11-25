package com.ebee.finance.dto;

import com.ebee.finance.entity.AccountTransaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AccountStatement(UUID accountId, String accountName, LocalDate from, LocalDate to,
                               BigDecimal openingBalance, BigDecimal closingBalance, List<Line> lines) {

    public record Line(UUID id, LocalDate date, AccountTransaction.Type type, AccountTransaction.Direction direction,
                       BigDecimal amount, BigDecimal balanceAfter, String referenceType, UUID referenceId,
                       String description) {
    }
}
