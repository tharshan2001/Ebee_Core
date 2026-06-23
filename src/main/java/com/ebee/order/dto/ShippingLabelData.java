package com.ebee.order.dto;

public record ShippingLabelData(
        String orderNumber,
        String customerName,
        String customerPhone,
        String customerAddress,
        int itemCount,
        String deliveryNotes
) {}
