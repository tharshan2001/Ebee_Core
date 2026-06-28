package com.ebee.driver.dto;

import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.order.enums.OrderStatus;
import com.ebee.order.enums.PaymentStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class DriverDeliveryResponse {
    private UUID orderId;
    private String orderNumber;
    private String customerName;
    private String customerPhone;
    private String customerStreet;
    private String customerCity;
    private String customerDistrict;
    private String customerPostalCode;
    private String district;
    private DeliveryStatus deliveryStatus;
    private OrderStatus orderStatus;
    private UUID driverId;
    private String driverName;
    private LocalDate scheduledDate;
    private String notes;
    private String deliveryNotes;
    private String deliveryFailureReason;
    private String deliveryPhotoUrl;
    private String deliverySignatureUrl;
    private LocalDateTime deliveredAt;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal outstandingBalance;
    private PaymentStatus paymentStatus;
    private List<OrderItemInfo> items;

    @Data
    public static class OrderItemInfo {
        private UUID productId;
        private String productName;
        private String productItemCode;
        private int quantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
    }
}
