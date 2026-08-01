package com.ebee.trip.dto;

import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.order.enums.OrderStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.UUID;

public record TripOrderMarkerDto(
        UUID orderId,
        String orderNumber,
        String customerName,
        String customerPhone,
        String street,
        String addressLine2,
        String city,
        String postalCode,
        String district,
        String country,
        DeliveryStatus deliveryStatus,
        OrderStatus orderStatus,
        BigDecimal totalAmount,
        String formattedAddress
) {
    @JsonProperty("status")
    public String getStatus() {
        return deliveryStatus != null ? deliveryStatus.name() : null;
    }
}
