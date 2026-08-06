package com.ebee.delivery.dto;

import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.order.enums.OrderStatus;
import com.ebee.order.enums.PaymentStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class DeliveryResponse {
    private UUID orderId;
    private String orderNumber;
    private OrderStatus orderStatus;
    private String customerName;
    private String customerPhone;
    private String customerStreet;
    private String customerAddressLine2;
    private String customerCity;
    private String customerPostalCode;
    private String customerDistrict;
    private String customerCountry;
    private String district;
    private DeliveryStatus status;
    private UUID driverId;
    private String driverName;
    private UUID tripId;
    private String tripName;
    private LocalDate scheduledDate;
    private String notes;
    private String deliveryNotes;
    private String driverNotes;
    private LocalDateTime deliveredAt;
    private String deliveryFailureReason;
    private String deliveryPhotoUrl;
    private String deliverySignatureUrl;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal outstandingBalance;
    private PaymentStatus paymentStatus;
    private LocalDateTime createdAt;
}
