package com.ebee.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record LedgerEntryResponse(
    UUID id,
    UUID entityId,
    String transactionType,
    BigDecimal debit,
    BigDecimal credit,
    BigDecimal balance,
    String description,
    LocalDateTime createdAt
) {}
