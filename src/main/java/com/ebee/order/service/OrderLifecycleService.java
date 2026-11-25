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
 * Order status changes after creation: forward status moves, cancellation and returns (split out of
 * OrderServiceImpl). Money and stock effects go through OrderSettlementService. OrderService delegates here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderLifecycleService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final CommissionLifecycleService commissionLifecycleService;
    private final CustomerRepository customerRepository;
    private final OrderNotificationService orderNotificationService;
    private final AuditLogService auditLogService;
    private final OrderSettlementService orderSettlementService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public OrderResponse updateOrderStatus(UUID id, OrderStatus status) {
        Order order = orderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

        if (order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.DELIVERED
                || order.getStatus() == OrderStatus.RETURNED) {
            throw new IllegalArgumentException("Cannot update status of a " + order.getStatus().name().toLowerCase() + " order");
        }

        if (status == OrderStatus.DELIVERED
                || status == OrderStatus.RETURNED
                || status == OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("Use delivery or cancel endpoints after shipping");
        }
        validateForwardTransition(order, status);

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(status);
        if (status == OrderStatus.SHIPPED && order.getDeliveryStatus() == null) {
            order.setDeliveryStatus(DeliveryStatus.PENDING);
            order.setDeliveryFailureReason(null);
            order.setDeliveryPhotoUrl(null);
            order.setDeliverySignatureUrl(null);
            order.setDeliveryScheduledDate(null);
            order.setDeliveryNotes(null);
            order.setTrip(null);
            order.setDeliveryAssignedTo(null);
        }
        Order updatedOrder = orderRepository.save(order);

        auditLogService.record(AuditAction.STATUS_CHANGE, "Order", updatedOrder.getId(),
                "Order " + updatedOrder.getOrderNumber() + " status changed from "
                + oldStatus + " to " + status);

        orderNotificationService.sendOrderStatus(updatedOrder);

        eventPublisher.publishEvent(new OrderStatusChangedEvent(this, updatedOrder, oldStatus, LocalDateTime.now()));

        return orderMapper.toResponse(updatedOrder);
    }

    /** Fulfilment order a manual status update may move along (forward only; steps may be skipped). */
    private static final List<OrderStatus> FULFILMENT_STEPS =
            List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.PROCESSING, OrderStatus.SHIPPED);

    /**
     * Manual updates only move forward: PENDING → CONFIRMED → PROCESSING → SHIPPED. Going back
     * (e.g. SHIPPED → PENDING while the order is on a trip) would desync delivery and stock.
     * Exception kept from before: SHIPPED again on a shipped order whose delivery record was
     * cleared, which re-initialises its delivery so it can be put on a trip.
     */
    private static void validateForwardTransition(Order order, OrderStatus target) {
        OrderStatus current = order.getStatus();
        if (current == OrderStatus.SHIPPED && target == OrderStatus.SHIPPED && order.getDeliveryStatus() == null) {
            return;
        }
        int from = FULFILMENT_STEPS.indexOf(current);
        int to = FULFILMENT_STEPS.indexOf(target);
        if (to < 0) {
            throw new IllegalArgumentException("Status " + target + " can't be set manually");
        }
        if (from < 0 || to <= from) {
            throw new IllegalArgumentException("Order " + order.getOrderNumber() + " can't go from " + current
                    + " to " + target + "; order status only moves forward (PENDING → CONFIRMED → PROCESSING → SHIPPED)");
        }
    }

    @Transactional
    public OrderResponse cancelOrder(UUID id, OrderSettlementRequest request) {
        Order order = orderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("Order is already cancelled");
        }

        if (order.getStatus() == OrderStatus.SHIPPED
                || order.getStatus() == OrderStatus.DELIVERED
                || order.getStatus() == OrderStatus.RETURNED) {
            throw new IllegalArgumentException("Cannot cancel a " + order.getStatus().name().toLowerCase() + " order");
        }

        boolean stockAlreadyRestored = order.getDeliveryStatus() == DeliveryStatus.FAILED_PERMANENT;

        commissionLifecycleService.cancelCommissionForOrder(id);

        BigDecimal previousOutstanding = order.getCustomer().getOutstandingBalance();
        BigDecimal previousAdvance = order.getCustomer().getAdvanceBalance();

        OrderSettlementService.Settlement settlement = orderSettlementService.cancel(
                order, request.refundChoice(), !stockAlreadyRestored, "Order cancelled");

        DuesChangeType changeType = !settlement.moneyReturned() ? DuesChangeType.CANCEL_UNPAID
                : settlement.toAdvance().signum() > 0 ? DuesChangeType.CANCEL_ADVANCE
                : DuesChangeType.CANCEL_REFUND;

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        Order updatedOrder = orderRepository.save(order);
        customerRepository.save(order.getCustomer());

        eventPublisher.publishEvent(new CustomerDuesChangedEvent(
                this, order.getCustomer(),
                previousOutstanding, order.getCustomer().getOutstandingBalance(),
                previousAdvance, order.getCustomer().getAdvanceBalance(),
                changeType
        ));

        auditLogService.record(AuditAction.STATUS_CHANGE, "Order", updatedOrder.getId(),
                "Order " + updatedOrder.getOrderNumber() + " cancelled");

        orderNotificationService.sendOrderCancelled(updatedOrder);

        eventPublisher.publishEvent(new OrderStatusChangedEvent(this, updatedOrder, oldStatus, LocalDateTime.now()));

        return orderMapper.toResponse(updatedOrder);
    }

    @Transactional
    public OrderResponse returnOrder(UUID id, ReturnOrderItemRequest request) {
        Order order = orderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

        if (order.getStatus() == OrderStatus.RETURNED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("Cannot return an order that is already " + order.getStatus());
        }
        // Same rule as return/exchange requests: only goods the customer actually received come back.
        // Orders that haven't been delivered yet are cancelled instead.
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalArgumentException("Only delivered orders can be returned (order " + order.getOrderNumber()
                    + " is " + order.getStatus() + "); cancel it instead");
        }

        Map<UUID, OrderItem> orderItemMap = order.getItems().stream()
                .collect(Collectors.toMap(OrderItem::getId, oi -> oi));

        List<OrderSettlementService.ReturnLine> lines = new ArrayList<>();
        if (request.items() == null || request.items().isEmpty()) {
            order.getItems().stream()
                    .filter(oi -> oi.getReturnableQuantity() > 0)
                    .forEach(oi -> lines.add(new OrderSettlementService.ReturnLine(oi, oi.getReturnableQuantity())));
        } else {
            for (ReturnItemRequest itemReq : request.items()) {
                OrderItem orderItem = orderItemMap.get(itemReq.orderItemId());
                if (orderItem == null) {
                    throw new IllegalArgumentException("Order item " + itemReq.orderItemId() + " does not belong to this order");
                }
                if (orderItem.getCombo() != null && itemReq.quantity() != orderItem.getQuantity()) {
                    throw new IllegalArgumentException("Combo items must be returned in full quantity");
                }
                lines.add(new OrderSettlementService.ReturnLine(orderItem, itemReq.quantity()));
            }
        }
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Nothing left to return on order " + order.getOrderNumber());
        }

        Customer customer = order.getCustomer();
        BigDecimal previousOutstanding = customer.getOutstandingBalance();
        BigDecimal previousAdvance = customer.getAdvanceBalance();

        OrderSettlementService.Settlement settlement = orderSettlementService.returnLines(
                order, lines, request.refundChoice(), "Order return");
        // Re-base commission on the reduced total (cancels it when everything came back).
        commissionLifecycleService.adjustCommissionForOrder(order);

        DuesChangeType changeType = !settlement.moneyReturned() ? DuesChangeType.CANCEL_UNPAID
                : settlement.toAdvance().signum() > 0 ? DuesChangeType.RETURN_ADVANCE
                : DuesChangeType.RETURN_REFUND;

        boolean allReturned = order.getItems().stream().allMatch(OrderItem::isFullyReturned);
        OrderStatus oldStatus = order.getStatus();
        if (allReturned) {
            order.setStatus(OrderStatus.RETURNED);
        }

        Order updatedOrder = orderRepository.save(order);
        customerRepository.save(customer);

        eventPublisher.publishEvent(new CustomerDuesChangedEvent(
                this, customer,
                previousOutstanding, customer.getOutstandingBalance(),
                previousAdvance, customer.getAdvanceBalance(),
                changeType
        ));

        auditLogService.record(AuditAction.STATUS_CHANGE, "Order", updatedOrder.getId(),
                "Order " + updatedOrder.getOrderNumber() + " returned");

        orderNotificationService.sendOrderStatus(updatedOrder);

        eventPublisher.publishEvent(new OrderStatusChangedEvent(this, updatedOrder, oldStatus, LocalDateTime.now()));

        return orderMapper.toResponse(updatedOrder);
    }
}
