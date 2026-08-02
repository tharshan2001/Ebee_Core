package com.ebee.supplier.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SupplierAdjustmentResponse(
        UUID supplierId,
        String supplierName,
        BigDecimal amountAdjusted,
        LocalDate effectiveDate,
        String description,
        BigDecimal newOutstandingBalance
) {}
