package com.ebee.driver.dto;

import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.order.enums.OrderStatus;
import com.ebee.order.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private LocalDateTime createdAt;
    private List<OrderItemInfo> items;
    private List<StatusTimelineEntry> timeline;

    @JsonProperty("status")
    public String getStatus() {
        return deliveryStatus != null ? deliveryStatus.name() : null;
    }

    @JsonProperty("customer")
    public CustomerInfo getCustomer() {
        return new CustomerInfo(customerName, customerPhone, buildAddress(), customerDistrict);
    }

    @JsonProperty("orderInfo")
    public OrderInfo getOrderInfo() {
        return new OrderInfo(orderNumber, totalAmount, paidAmount, outstandingBalance,
                paymentStatus != null ? paymentStatus.name() : null);
    }

    @JsonProperty("photoUrl")
    public String getPhotoUrl() {
        return deliveryPhotoUrl;
    }

    @JsonProperty("signatureUrl")
    public String getSignatureUrl() {
        return deliverySignatureUrl;
    }

    private String buildAddress() {
        StringBuilder sb = new StringBuilder();
        if (customerStreet != null) sb.append(customerStreet);
        if (customerCity != null) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(customerCity);
        }
        if (customerPostalCode != null) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(customerPostalCode);
        }
        return sb.toString();
    }

    @Data
    public static class OrderItemInfo {
        private UUID productId;
        private String productName;
        private String productItemCode;
        private int quantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;

        @JsonProperty("name")
        public String getName() {
            return productName;
        }
    }

    @Data
    public static class CustomerInfo {
        private final String name;
        private final String phone;
        private final String address;
        private final String district;
    }

    @Data
    public static class OrderInfo {
        private final String orderNumber;
        private final BigDecimal total;
        private final BigDecimal paid;
        private final BigDecimal outstanding;
        private final String paymentStatus;

        public String getPaymentStatus() {
            return paymentStatus != null ? paymentStatus : null;
        }
    }

    @Data
    public static class StatusTimelineEntry {
        private final String status;
        private final LocalDateTime timestamp;
        private final String note;
    }
}