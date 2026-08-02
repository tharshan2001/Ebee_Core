package com.ebee.supplier.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record SupplierOutstandingOrdersResponse(
        UUID supplierId,
        String supplierName,
        BigDecimal totalOutstanding,
        java.util.List<PurchaseOrderOutstanding> orders
) {
    public record PurchaseOrderOutstanding(
            UUID orderId,
            String poNumber,
            LocalDateTime receivedAt,
            BigDecimal totalAmount,
            BigDecimal paidAmount,
            BigDecimal outstandingBalance
    ) {}
}
