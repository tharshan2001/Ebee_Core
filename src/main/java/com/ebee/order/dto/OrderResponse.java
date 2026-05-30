package com.ebee.order.dto;

import com.ebee.order.enums.OrderStatus;
import lombok.Data;

import java.math.BigDecimal;
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
    private OrderStatus status;
    private String notes;
    private UUID createdById;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<OrderItemResponse> items;
}
