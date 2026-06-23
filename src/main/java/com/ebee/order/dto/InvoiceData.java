package com.ebee.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record InvoiceData(
        String title,
        String orderNumber,
        LocalDateTime createdAt,
        String customerName,
        String customerPhone,
        String customerAddress,
        String agentName,
        String agentCode,
        List<LineItem> items,
        BigDecimal subtotal,
        BigDecimal discountTotal,
        BigDecimal totalAmount,
        String notes
) {
    public record LineItem(
            String productName,
            String productCode,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal,
            String discount
    ) {}
}
