package com.ebee.order.service;

import com.ebee.audit.enums.AuditAction;
import com.ebee.audit.service.AuditLogService;
import com.ebee.combo.entity.Combo;
import com.ebee.commission.service.CommissionLifecycleService;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.security.SecurityService;
import com.ebee.customer.entity.Customer;
import com.ebee.customer.enums.DuesChangeType;
import com.ebee.customer.event.CustomerDuesChangedEvent;
import com.ebee.customer.repository.CustomerRepository;
import com.ebee.order.dto.*;
import com.ebee.order.entity.Order;
import com.ebee.order.entity.OrderItem;
import com.ebee.order.entity.ReturnExchangeRequest;
import com.ebee.order.entity.ReturnRequestItem;
import com.ebee.order.enums.OrderStatus;
import com.ebee.order.enums.RefundChoice;
import com.ebee.order.enums.ReturnExchangeResolution;
import com.ebee.order.enums.ReturnExchangeStatus;
import com.ebee.order.event.OrderStatusChangedEvent;
import com.ebee.order.repository.OrderRepository;
import com.ebee.order.repository.ReturnExchangeRepository;
import com.ebee.user.entity.User;
import com.ebee.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReturnExchangeService {

    private final ReturnExchangeRepository returnExchangeRepository;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final CommissionLifecycleService commissionLifecycleService;
    private final OrderNotificationService orderNotificationService;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final SecurityService securityService;
    private final com.ebee.customer.service.CustomerService customerService;
    private final OrderSettlementService orderSettlementService;

    @Transactional
    public ReturnExchangeResponse initiateReturnExchange(UUID orderId, InitiateReturnExchangeRequest request) {
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalArgumentException("Can only initiate return exchange for DELIVERED orders");
        }

        if (returnExchangeRepository.existsActiveByOrderId(orderId)) {
            throw new IllegalArgumentException("Return exchange already requested for this order");
        }

        Map<UUID, OrderItem> orderItemMap = order.getItems().stream()
                .collect(Collectors.toMap(OrderItem::getId, oi -> oi));

        List<ReturnRequestItem> returnItems = new ArrayList<>();
        BigDecimal totalRequestedAmount = BigDecimal.ZERO;

        for (ReturnItemRequest itemReq : request.items()) {
            OrderItem orderItem = orderItemMap.get(itemReq.orderItemId());
            if (orderItem == null) {
                throw new IllegalArgumentException("Order item " + itemReq.orderItemId() + " does not belong to this order");
            }
            if (itemReq.quantity() > orderItem.getReturnableQuantity()) {
                throw new IllegalArgumentException(
                        "Cannot return " + itemReq.quantity() + " units of " + orderItem.getProduct().getName()
                                + ". Returnable: " + orderItem.getReturnableQuantity());
            }
            if (orderItem.getCombo() != null && itemReq.quantity() != orderItem.getQuantity()) {
                throw new IllegalArgumentException("Combo items must be returned in full quantity");
            }

            BigDecimal unitRefund = orderItem.getUnitPrice();
            BigDecimal subtotal = unitRefund.multiply(BigDecimal.valueOf(itemReq.quantity()));

            ReturnRequestItem rri = ReturnRequestItem.builder()
                    .orderItem(orderItem)
                    .quantity(itemReq.quantity())
                    .unitRefundAmount(unitRefund)
                    .subtotal(subtotal)
                    .build();
            returnItems.add(rri);
            totalRequestedAmount = totalRequestedAmount.add(subtotal);
        }

        ReturnExchangeRequest returnRequest = ReturnExchangeRequest.builder()
                .order(order)
                .requestedAmount(totalRequestedAmount)
                .status(ReturnExchangeStatus.PENDING_REVIEW)
                .reason(request.reason())
                .items(new ArrayList<>())
                .build();

        for (ReturnRequestItem rri : returnItems) {
            rri.setReturnRequest(returnRequest);
            returnRequest.getItems().add(rri);
        }

        returnExchangeRepository.save(returnRequest);
        log.info("Return exchange initiated for order {}: {} items, requestedAmount={}",
                order.getOrderNumber(), returnItems.size(), totalRequestedAmount);

        return toResponse(returnRequest);
    }

    @Transactional
    public ReturnExchangeResponse reviewReturnExchange(UUID returnExchangeId, ReviewReturnExchangeRequest request, UUID reviewedByUserId) {
        ReturnExchangeRequest returnRequest = returnExchangeRepository.findById(returnExchangeId)
                .orElseThrow(() -> new ResourceNotFoundException("ReturnExchangeRequest", "id", returnExchangeId));

        if (returnRequest.getStatus() != ReturnExchangeStatus.PENDING_REVIEW) {
            throw new IllegalArgumentException("Return exchange request is not pending review");
        }

        User reviewedBy = userRepository.findById(reviewedByUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", reviewedByUserId));

        Order order = returnRequest.getOrder();
        Customer customer = order.getCustomer();
        if (customer == null) {
            throw new IllegalArgumentException("Order has no customer assigned");
        }

        returnRequest.setApprovedAmount(request.approvedAmount());
        returnRequest.setResolutionType(request.resolutionType());
        returnRequest.setNotes(request.notes());
        returnRequest.setReviewedBy(reviewedBy);
        returnRequest.setReviewedAt(LocalDateTime.now());

        if (request.resolutionType() == ReturnExchangeResolution.REJECTED) {
            returnRequest.setStatus(ReturnExchangeStatus.REJECTED);

            ReturnExchangeRequest saved = returnExchangeRepository.save(returnRequest);

            auditLogService.record(AuditAction.STATUS_CHANGE, "Order", order.getId(),
                    "Return exchange rejected for Order " + order.getOrderNumber()
                            + " - Reason: " + (request.notes() != null ? request.notes() : "N/A"));

            orderNotificationService.sendOrderStatus(order);

            log.info("Return exchange REJECTED for order {}: reason={}", order.getOrderNumber(), request.notes());
            return toResponse(saved);
        }

        returnRequest.setStatus(ReturnExchangeStatus.APPROVED);

        List<OrderSettlementService.ReturnLine> lines = returnRequest.getItems().stream()
                .map(rri -> new OrderSettlementService.ReturnLine(rri.getOrderItem(), rri.getQuantity()))
                .toList();

        commissionLifecycleService.cancelCommissionForOrder(order.getId());

        BigDecimal previousOutstanding = customer.getOutstandingBalance();
        BigDecimal previousAdvance = customer.getAdvanceBalance();

        RefundChoice refundChoice = request.resolutionType() == ReturnExchangeResolution.ADVANCE
                ? RefundChoice.CREDIT_ADVANCE : RefundChoice.REFUND;
        orderSettlementService.returnLines(order, lines, refundChoice,
                "Return exchange (" + request.resolutionType() + ")");

        boolean allReturned = order.getItems().stream().allMatch(OrderItem::isFullyReturned);
        OrderStatus oldStatus = order.getStatus();
        if (allReturned) {
            order.setStatus(OrderStatus.RETURNED);
        }

        Order updatedOrder = orderRepository.save(order);
        returnExchangeRepository.save(returnRequest);
        customerRepository.save(customer);

        DuesChangeType changeType = request.resolutionType() == ReturnExchangeResolution.ADVANCE
                ? DuesChangeType.RETURN_ADVANCE : DuesChangeType.RETURN_REFUND;
        eventPublisher.publishEvent(new CustomerDuesChangedEvent(
                this, customer,
                previousOutstanding, customer.getOutstandingBalance(),
                previousAdvance, customer.getAdvanceBalance(),
                changeType
        ));

        auditLogService.record(AuditAction.STATUS_CHANGE, "Order", updatedOrder.getId(),
                "Return exchange approved for Order " + updatedOrder.getOrderNumber()
                        + " - Resolution: " + request.resolutionType());

        orderNotificationService.sendOrderStatus(updatedOrder);

        eventPublisher.publishEvent(new OrderStatusChangedEvent(this, updatedOrder, oldStatus, LocalDateTime.now()));

        log.info("Return exchange APPROVED for order {}: approvedAmount={}, resolution={}",
                order.getOrderNumber(), request.approvedAmount(), request.resolutionType());

        return toResponse(returnRequest);
    }

    @Transactional(readOnly = true)
    public List<ReturnExchangeResponse> getPendingReviews() {
        return returnExchangeRepository.findByStatusOrderByCreatedAtDesc(ReturnExchangeStatus.PENDING_REVIEW)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReturnExchangeResponse getByOrderId(UUID orderId) {
        ReturnExchangeRequest request = returnExchangeRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("ReturnExchangeRequest", "orderId", orderId));
        return toResponse(request);
    }

    @Transactional(readOnly = true)
    public long countPending() {
        return returnExchangeRepository.countByStatus(ReturnExchangeStatus.PENDING_REVIEW);
    }

    private ReturnExchangeResponse toResponse(ReturnExchangeRequest request) {
        Order order = request.getOrder();
        Customer customer = order.getCustomer();

        String reviewedByName = null;
        if (request.getReviewedBy() != null) {
            User reviewedByUser = request.getReviewedBy();
            reviewedByName = reviewedByUser.getFirstName() + " " + reviewedByUser.getLastName();
        }

        String customerName = customer != null ? customer.getFirstName() + " " + customer.getLastName() : "N/A";

        List<ReturnRequestItemResponse> itemResponses = request.getItems().stream()
                .map(rri -> new ReturnRequestItemResponse(
                        rri.getId(),
                        rri.getOrderItem().getId(),
                        rri.getOrderItem().getProduct() != null ? rri.getOrderItem().getProduct().getName() : "Combo",
                        rri.getOrderItem().getProduct() != null ? rri.getOrderItem().getProduct().getItemCode() : null,
                        rri.getQuantity(),
                        rri.getUnitRefundAmount(),
                        rri.getSubtotal()
                ))
                .toList();

        return new ReturnExchangeResponse(
                request.getId(),
                order.getId(),
                order.getOrderNumber(),
                customer != null ? customer.getId() : null,
                customerName,
                request.getRequestedAmount(),
                request.getApprovedAmount(),
                request.getStatus(),
                request.getResolutionType(),
                request.getReason(),
                request.getNotes(),
                reviewedByName,
                request.getReviewedAt(),
                request.getCreatedAt(),
                itemResponses
        );
    }
}
