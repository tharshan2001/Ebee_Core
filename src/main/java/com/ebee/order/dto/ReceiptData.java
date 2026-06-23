package com.ebee.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ReceiptData(
        String title,
        String orderNumber,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        String customerName,
        String customerPhone,
        String customerAddress,
        String agentName,
        String agentCode,
        String paymentMethod,
        List<InvoiceData.LineItem> items,
        BigDecimal subtotal,
        BigDecimal discountTotal,
        BigDecimal totalAmount,
        String notes
) {}
