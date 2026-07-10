package com.ebee.purchase.dto;

import com.ebee.purchase.enums.PurchaseOrderStatus;
import com.ebee.purchase.enums.PurchasePaymentMethod;
import com.ebee.purchase.enums.PurchasePaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderResponse(
        UUID id,
        String poNumber,
        UUID supplierId,
        String supplierName,
        PurchaseOrderStatus status,
        BigDecimal totalAmount,
        PurchasePaymentMethod paymentMethod,
        PurchasePaymentStatus paymentStatus,
        BigDecimal paidAmount,
        BigDecimal outstandingBalance,
        LocalDateTime paidAt,
        String notes,
        UUID createdById,
        String createdByName,
        List<PurchaseOrderItemResponse> items,
        List<AttachmentResponse> attachments,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
