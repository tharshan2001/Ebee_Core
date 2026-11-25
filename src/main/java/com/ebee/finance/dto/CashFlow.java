package com.ebee.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Money in and out of all accounts in a period. Transfers between own accounts are left out. */
public record CashFlow(
        LocalDate from, LocalDate to,
        BigDecimal openingCash,
        List<Line> operating, BigDecimal netOperating,
        List<Line> financing, BigDecimal netFinancing,
        List<Line> other, BigDecimal netOther,
        BigDecimal netChange,
        BigDecimal closingCash) {

    public record Line(String label, BigDecimal amount) {
    }
}
