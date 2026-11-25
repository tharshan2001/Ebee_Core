package com.ebee.order.dto;

import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.order.enums.OrderSource;
import com.ebee.order.enums.OrderStatus;
import com.ebee.order.enums.PaymentMethod;
import com.ebee.order.enums.PaymentStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class OrderResponse {

    private UUID id;
    private String orderNumber;
    private UUID agentId;
    private String agentCode;
    private String agentName;
    private UUID customerId;
    private String customerName;
    private String customerPhone;
    private BigDecimal totalAmount;
    /** VAT included in totalAmount. */
    private BigDecimal taxTotal;
    private OrderStatus status;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
    private BigDecimal paidAmount;
    private LocalDateTime paidAt;
    private BigDecimal outstandingBalance;
    private String notes;
    private UUID createdById;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<OrderItemResponse> items;

    private String district;
    private DeliveryStatus deliveryStatus;
    private UUID deliveryAssignedToId;
    private String deliveryAssignedToName;
    private LocalDate deliveryScheduledDate;
    private String deliveryNotes;
    private String deliveryFailureReason;
    private String deliveryPhotoUrl;
    private String deliverySignatureUrl;
    private String driverNotes;
    private LocalDateTime deliveredAt;
    private OrderSource orderSource;
}
