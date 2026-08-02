package com.ebee.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record SupplierLedgerEntryResponse(
    UUID id,
    UUID supplierId,
    UUID purchaseOrderId,
    UUID supplierPaymentId,
    String transactionType,
    BigDecimal debit,
    BigDecimal credit,
    String description,
    LocalDate effectiveDate,
    String reference,
    LocalDateTime createdAt
) {}
