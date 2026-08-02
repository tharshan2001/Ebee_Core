package com.ebee.supplier.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SupplierPaymentResponse(
        UUID supplierId,
        String supplierName,
        BigDecimal amountPaid,
        int ordersSettled,
        BigDecimal remainingOutstanding
) {}
