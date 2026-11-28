package com.ebee.report.dto;

import com.ebee.common.response.PageResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One page of a party ledger, newest first. Each row carries that party's balance after the entry.
 * The balances cover the whole book: customer = what they owe us, supplier and agent = what we owe them.
 */
public record LedgerPage(PageResponse<Row> page, BigDecimal customerBalance, BigDecimal supplierBalance,
                         BigDecimal agentBalance) {

    public record Row(UUID id, LocalDateTime createdAt, String code, String name, String transactionType,
                      String description, BigDecimal debit, BigDecimal credit, BigDecimal balance) {
    }
}
