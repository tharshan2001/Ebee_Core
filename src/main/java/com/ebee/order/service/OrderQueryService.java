package com.ebee.order.service;

import com.ebee.accounting.service.LedgerService;
import com.ebee.agent.entity.Agent;
import com.ebee.agent.repository.AgentRepository;
import com.ebee.audit.enums.AuditAction;
import com.ebee.audit.service.AuditLogService;
import com.ebee.combo.entity.Combo;
import com.ebee.combo.entity.ComboItem;
import com.ebee.combo.entity.ComboRule;
import com.ebee.combo.enums.ComboRuleType;
import com.ebee.combo.repository.ComboRepository;
import com.ebee.commission.service.CommissionCalculationService;
import com.ebee.commission.service.CommissionLifecycleService;
import com.ebee.common.enums.District;
import com.ebee.common.response.PageResponse;
import com.ebee.customer.entity.Customer;
import com.ebee.customer.enums.DuesChangeType;
import com.ebee.customer.event.CustomerDuesChangedEvent;
import com.ebee.customer.repository.CustomerRepository;
import com.ebee.customer.service.CustomerService;
import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.inventory.service.InventoryService;
import com.ebee.commission.entity.Commission;
import com.ebee.product.entity.Product;
import com.ebee.product.repository.ProductRepository;
import com.ebee.user.entity.User;
import com.ebee.user.repository.UserRepository;
import com.ebee.order.dto.OrderItemRequest;
import com.ebee.order.dto.OrderRequest;
import com.ebee.order.dto.OrderResponse;
import com.ebee.order.dto.OrderSettlementRequest;
import com.ebee.order.dto.PaymentResponse;
import com.ebee.order.dto.RecordPaymentRequest;
import com.ebee.order.dto.ReturnItemRequest;
import com.ebee.order.dto.ReturnOrderItemRequest;
import com.ebee.order.dto.UseAdvanceRequest;
import com.ebee.order.entity.Order;
import com.ebee.order.entity.OrderItem;
import com.ebee.order.entity.Payment;
import com.ebee.order.enums.OrderSource;
import com.ebee.order.enums.OrderStatus;
import com.ebee.order.enums.PaymentMethod;
import com.ebee.order.enums.PaymentStatus;
import com.ebee.order.enums.RefundChoice;
import com.ebee.order.event.OrderStatusChangedEvent;
import com.ebee.order.exception.DuplicateOrderNumberException;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.security.SecurityService;
import com.ebee.order.mapper.OrderMapper;
import com.ebee.order.repository.OrderRepository;
import com.ebee.order.repository.PaymentRepository;
import com.ebee.order.util.OrderItemSummaryBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read-only order lookups and listings (split out of OrderServiceImpl). OrderService delegates here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private final OrderRepository orderRepository;
    private final AgentRepository agentRepository;
    private final OrderMapper orderMapper;
    private final SecurityService securityService;
    private final CustomerService customerService;

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID id) {
        Order order = orderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));
        return orderMapper.toResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderByOrderNumber(String orderNumber) {
        Order order = orderRepository.findByOrderNumberWithDetails(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderNumber", orderNumber));
        return orderMapper.toResponse(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByAgentId(UUID agentId) {
        agentRepository.findById(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "id", agentId));
        List<Order> orders = orderRepository.findByAgentIdWithDetails(agentId);
        return orderMapper.toResponseList(orders);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders() {
        UUID userId = securityService.getCurrentUserId();
        Agent agent = agentRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "userId", userId));
        List<Order> orders = orderRepository.findByAgentIdWithDetails(agent.getId());
        return orderMapper.toResponseList(orders);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByCustomerId(UUID customerId) {
        customerService.getCustomerById(customerId);
        List<Order> orders = orderRepository.findByCustomerIdWithDetails(customerId);
        return orderMapper.toResponseList(orders);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByCustomerId(UUID customerId, List<PaymentStatus> paymentStatuses) {
        List<OrderResponse> allOrders = getOrdersByCustomerId(customerId);
        if (paymentStatuses == null || paymentStatuses.isEmpty()) return allOrders;
        Set<PaymentStatus> statuses = EnumSet.copyOf(paymentStatuses);
        return allOrders.stream()
                .filter(o -> statuses.contains(o.getPaymentStatus()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        List<Order> orders = orderRepository.findAllWithDetails();
        return orderMapper.toResponseList(orders);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getUnassignedOrders() {
        List<Order> orders = orderRepository.findByTripIsNull();
        return orderMapper.toResponseList(orders);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getUnassignedOrdersByStatus(OrderStatus status) {
        List<Order> orders = orderRepository.findByTripIsNullAndStatus(status);
        return orderMapper.toResponseList(orders);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getUnassignedOrdersIncludingFailed() {
        List<Order> shipped = orderRepository.findByTripIsNullAndStatus(OrderStatus.SHIPPED);
        return orderMapper.toResponseList(shipped);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getUnassignedOrdersPaginated(Pageable pageable) {
        Page<Order> ordersPage = orderRepository.findUnassignedOrdersPaged(pageable);
        return PageResponse.from(ordersPage.map(orderMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrdersPaginated(Pageable pageable) {
        Page<Order> orders = orderRepository.findAllWithDetails(pageable);
        return orders.map(orderMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrdersByStatusPaginated(OrderStatus status, Pageable pageable) {
        Page<Order> orders = orderRepository.findByStatusWithDetails(status, pageable);
        return orders.map(orderMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getCustomerOrdersPaginated(UUID customerId, Pageable pageable) {
        customerService.getCustomerById(customerId);
        Page<Order> orders = orderRepository.findByCustomerIdWithDetailsFull(customerId, pageable);
        return orders.map(orderMapper::toResponse);
    }
}
