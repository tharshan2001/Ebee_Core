package com.ebee.order.service;

import com.ebee.order.dto.OrderRequest;
import com.ebee.order.dto.OrderResponse;
import com.ebee.order.dto.RecordPaymentRequest;
import com.ebee.order.enums.OrderStatus;
import com.ebee.order.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface OrderService {

    OrderResponse createOrder(OrderRequest request);

    OrderResponse getOrderById(UUID id);

    OrderResponse getOrderByOrderNumber(String orderNumber);

    List<OrderResponse> getOrdersByAgentId(UUID agentId);

    List<OrderResponse> getMyOrders();

    List<OrderResponse> getOrdersByCustomerId(UUID customerId);

    List<OrderResponse> getOrdersByCustomerId(UUID customerId, List<PaymentStatus> paymentStatuses);

    List<OrderResponse> getAllOrders();

    Page<OrderResponse> getAllOrdersPaginated(Pageable pageable);

    Page<OrderResponse> getOrdersByStatusPaginated(OrderStatus status, Pageable pageable);

    OrderResponse updateOrderStatus(UUID id, OrderStatus status);

    OrderResponse cancelOrder(UUID id);

    OrderResponse returnOrder(UUID id);

    byte[] generateInvoicePdf(UUID id);

    byte[] generateReceiptPdf(UUID id);

    byte[] generateShippingLabelPdf(UUID id);

    OrderResponse recordPayment(UUID id, RecordPaymentRequest request);
}
