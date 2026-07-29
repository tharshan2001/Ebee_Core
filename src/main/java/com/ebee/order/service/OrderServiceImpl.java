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
import com.ebee.stock.enums.StockMovementType;
import com.ebee.user.entity.User;
import com.ebee.user.repository.UserRepository;
import com.ebee.order.dto.OrderItemRequest;
import com.ebee.order.dto.OrderRequest;
import com.ebee.order.dto.OrderResponse;
import com.ebee.order.dto.OrderSettlementRequest;
import com.ebee.order.dto.PaymentResponse;
import com.ebee.order.dto.RecordPaymentRequest;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final AgentRepository agentRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderMapper orderMapper;
    private final SecurityService securityService;
    private final InventoryService inventoryService;
    private final OrderNumberService orderNumberService;
    private final CommissionCalculationService commissionCalculationService;
    private final CommissionLifecycleService commissionLifecycleService;
    private final CustomerRepository customerRepository;
    private final CustomerService customerService;
    private final OrderNotificationService orderNotificationService;
    private final AuditLogService auditLogService;
    private final OrderPaymentService orderPaymentService;
    private final OrderPdfService orderPdfService;
    private final LedgerService ledgerService;
    private final ComboRepository comboRepository;
    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Retryable(retryFor = DuplicateOrderNumberException.class, maxAttempts = 5, backoff = @Backoff(delay = 50, multiplier = 2))
    @Transactional
    @Override
    public OrderResponse createOrder(OrderRequest request) {
        Agent agent = null;

        if (!securityService.isAdmin()) {
            UUID userId = securityService.getCurrentUserId();
            agent = agentRepository.findByUserId(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Agent", "userId", userId));
            if (!Boolean.TRUE.equals(agent.getIsActive())) {
                throw new IllegalArgumentException("Agent is not active");
            }
        } else if (request.getAgentCode() != null) {
            agent = agentRepository.findByAgentCode(request.getAgentCode())
                    .orElseThrow(() -> new ResourceNotFoundException("Agent", "agentCode", request.getAgentCode()));

            if (!Boolean.TRUE.equals(agent.getIsActive())) {
                throw new IllegalArgumentException("Agent is not active");
            }
        }

        User currentUser = userRepository.findById(securityService.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", securityService.getCurrentUserId()));

        Customer customer = customerService.findByPhone(request.getCustomerPhoneNumber());

        OrderSource orderSource = agent != null ? OrderSource.AGENT : OrderSource.DIRECT;
        District district = (customer.getAddress() != null) ? customer.getAddress().getDistrict() : null;
        if (district == null) {
            throw new IllegalArgumentException(
                    "Customer has no district. Update their address first.");
        }
        String orderNumber = orderNumberService.generateOrderNumber(district, orderSource);

        Order order = Order.builder()
                .agent(agent)
                .customer(customer)
                .createdBy(currentUser)
                .orderNumber(orderNumber)
                .notes(request.getNotes())
                .status(OrderStatus.PENDING)
                .orderSource(orderSource)
                .paymentMethod(request.getPaymentMethod())
                .items(new ArrayList<>())
                .build();

        List<UUID> stockProductIds = new ArrayList<>();
        List<Integer> stockQuantities = new ArrayList<>();
        List<String> stockNotes = new ArrayList<>();

        BigDecimal totalAmount = BigDecimal.ZERO;

        List<OrderItemRequest> regularItems = new ArrayList<>();
        List<OrderItemRequest> comboItems = new ArrayList<>();

        for (OrderItemRequest itemRequest : request.getItems()) {
            if (itemRequest.getComboId() != null) {
                comboItems.add(itemRequest);
            } else {
                regularItems.add(itemRequest);
            }
        }

        if (!regularItems.isEmpty()) {
            List<String> itemCodes = regularItems.stream()
                    .map(OrderItemRequest::getProductItemCode)
                    .toList();
            Map<String, Product> productMap = productRepository.findByItemCodeIn(itemCodes).stream()
                    .collect(Collectors.toMap(Product::getItemCode, Function.identity()));

            for (String itemCode : itemCodes) {
                Product product = productMap.get(itemCode);
                if (product == null) {
                    throw new ResourceNotFoundException("Product", "itemCode", itemCode);
                }
                if (!Boolean.TRUE.equals(product.getIsActive())) {
                    throw new IllegalArgumentException("Product is not active: " + product.getName());
                }
            }

            for (int i = 0; i < regularItems.size(); i++) {
                OrderItemRequest itemRequest = regularItems.get(i);
                Product product = productMap.get(itemRequest.getProductItemCode());

                BigDecimal originalUnitPrice = product.getPrice();
                String discountType = itemRequest.getDiscountType() != null
                        ? itemRequest.getDiscountType().name()
                        : null;
                BigDecimal unitPrice = product.getPriceWithDiscount(
                        discountType,
                        itemRequest.getDiscountValue());
                BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(itemRequest.getQuantity()));

                OrderItem item = OrderItem.builder()
                        .order(order)
                        .product(product)
                        .quantity(itemRequest.getQuantity())
                        .unitPrice(unitPrice)
                        .originalUnitPrice(originalUnitPrice)
                        .subtotal(subtotal)
                        .discountType(discountType)
                        .discountValue(itemRequest.getDiscountValue())
                        .build();

                order.getItems().add(item);
                totalAmount = totalAmount.add(subtotal);

                stockProductIds.add(product.getId());
                stockQuantities.add(itemRequest.getQuantity());
                stockNotes.add("Order " + orderNumber + " - Item " + (i + 1));
            }
        }

        for (OrderItemRequest itemRequest : comboItems) {
            Combo combo = comboRepository.findByIdWithDetails(itemRequest.getComboId())
                    .orElseThrow(() -> new ResourceNotFoundException("Combo", "id", itemRequest.getComboId()));

            if (!Boolean.TRUE.equals(combo.getIsActive())) {
                throw new IllegalArgumentException("Combo '" + combo.getName() + "' is not active");
            }

            for (ComboItem ci : combo.getItems()) {
                if (!Boolean.TRUE.equals(ci.getProduct().getIsActive())) {
                    throw new IllegalArgumentException(
                            "Combo '" + combo.getName() + "' contains inactive product: "
                                    + ci.getProduct().getName()
                                    + ". Remove or replace it before ordering.");
                }
            }

            ComboRule rule = combo.getRule();
            if (rule != null && rule.getRuleType() == ComboRuleType.FREE_PRODUCT) {
                if (!Boolean.TRUE.equals(rule.getFreeProduct().getIsActive())) {
                    throw new IllegalArgumentException(
                            "Combo '" + combo.getName() + "' free product '"
                                    + rule.getFreeProduct().getName() + "' is no longer available.");
                }
            }

            List<UUID> comboStockProductIds = new ArrayList<>();
            List<Integer> comboStockQuantities = new ArrayList<>();
            List<String> comboStockNotes = new ArrayList<>();

            for (ComboItem ci : combo.getItems()) {
                comboStockProductIds.add(ci.getProduct().getId());
                comboStockQuantities.add(ci.getQuantity());
                comboStockNotes.add("Order " + orderNumber + " - Combo " + combo.getName()
                        + " - " + ci.getProduct().getName());
            }

            if (rule != null && rule.getRuleType() == ComboRuleType.FREE_PRODUCT
                    && !Boolean.TRUE.equals(itemRequest.getApplyFreeProductAsDiscount())) {
                comboStockProductIds.add(rule.getFreeProduct().getId());
                comboStockQuantities.add(1);
                comboStockNotes.add("Order " + orderNumber + " - Combo " + combo.getName()
                        + " - FREE " + rule.getFreeProduct().getName());
            }

            inventoryService.checkStockAvailability(comboStockProductIds, comboStockQuantities);

            stockProductIds.addAll(comboStockProductIds);
            stockQuantities.addAll(comboStockQuantities);
            stockNotes.addAll(comboStockNotes);

            Map<UUID, BigDecimal> avgCosts = inventoryService.getAverageCosts(comboStockProductIds);

            BigDecimal comboPrice = combo.calculateComboPrice();
            BigDecimal originalUnitPrice = comboPrice;
            BigDecimal unitPrice;

            if (rule == null) {
                unitPrice = comboPrice;
            } else if (rule.getRuleType() == ComboRuleType.FREE_PRODUCT) {
                if (Boolean.TRUE.equals(itemRequest.getApplyFreeProductAsDiscount())) {
                    // Deduct free product price from combo total instead of adding free item
                    unitPrice = comboPrice.subtract(rule.getFreeProduct().getPrice()).max(BigDecimal.ZERO);
                } else {
                    unitPrice = comboPrice;

                    BigDecimal freeProductAvgCost = avgCosts.getOrDefault(
                            rule.getFreeProduct().getId(), BigDecimal.ZERO);

                    OrderItem freeItem = OrderItem.builder()
                            .order(order)
                            .product(rule.getFreeProduct())
                            .quantity(1)
                            .unitPrice(BigDecimal.ZERO)
                            .originalUnitPrice(rule.getFreeProduct().getPrice())
                            .subtotal(BigDecimal.ZERO)
                            .costPrice(freeProductAvgCost.setScale(2, RoundingMode.HALF_UP))
                            .isFreeProduct(true)
                            .build();

                    order.getItems().add(freeItem);
                }
            } else if (rule.getRuleType() == ComboRuleType.DISCOUNT) {
                unitPrice = switch (rule.getDiscountType()) {
                    case "FIXED" -> comboPrice.subtract(rule.getDiscountValue()).max(BigDecimal.ZERO);
                    case "PERCENTAGE" -> comboPrice.multiply(
                            BigDecimal.ONE.subtract(rule.getDiscountValue()
                                    .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)))
                            .max(BigDecimal.ZERO)
                            .setScale(2, RoundingMode.HALF_UP);
                    // default branch is defensive — DB CHECK guarantees FIXED or PERCENTAGE.
                    // Exists to prevent NPE if a rule was saved before validation was added.
                    default -> comboPrice;
                };
            } else {
                unitPrice = comboPrice;
            }

            BigDecimal comboCostPrice = BigDecimal.ZERO;
            for (ComboItem ci : combo.getItems()) {
                BigDecimal avgCost = avgCosts.get(ci.getProduct().getId());
                if (avgCost != null && avgCost.compareTo(BigDecimal.ZERO) > 0) {
                    comboCostPrice = comboCostPrice.add(
                            avgCost.multiply(BigDecimal.valueOf(ci.getQuantity())));
                }
            }

            OrderItem comboItem = OrderItem.builder()
                    .order(order)
                    .product(null)
                    .combo(combo)
                    .quantity(1)
                    .unitPrice(unitPrice)
                    .originalUnitPrice(originalUnitPrice)
                    .subtotal(unitPrice)
                    .costPrice(comboCostPrice.setScale(2, RoundingMode.HALF_UP))
                    .build();

            order.getItems().add(comboItem);
            totalAmount = totalAmount.add(unitPrice);
        }

        inventoryService.recordOrderStocks(stockProductIds, stockQuantities, StockMovementType.OUT, stockNotes);

        order.setTotalAmount(totalAmount);
        orderPaymentService.initPayment(order, request.getPaymentMethod(), request.getPaidAmount(), request.getChequeNumber(), request.getProofFileUrl());
        Order savedOrder;
        try {
            savedOrder = orderRepository.save(order);
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateOrderNumber(ex)) {
                throw new DuplicateOrderNumberException("Duplicate order number: " + order.getOrderNumber(), ex);
            }
            throw ex;
        }

        if (customer != null) {
            ledgerService.recordCustomerInvoice(
                    customer.getId(),
                    savedOrder.getId(),
                    savedOrder.getTotalAmount(),
                    "Order " + savedOrder.getOrderNumber(),
                    currentUser.getId()
            );
        }

        if (customer != null) {
            BigDecimal previousOutstanding = customer.getOutstandingBalance();
            BigDecimal previousAdvance = customer.getAdvanceBalance();
            orderPaymentService.syncCustomerOutstanding(customer);
            customerRepository.save(customer);
            eventPublisher.publishEvent(new CustomerDuesChangedEvent(
                    this, customer,
                    previousOutstanding, customer.getOutstandingBalance(),
                    previousAdvance, customer.getAdvanceBalance(),
                    DuesChangeType.ORDER_CREATED
            ));
        } else {
            orderPaymentService.syncCustomerOutstanding(order.getCustomer());
        }

        // Record initial payment in payments table
        if (savedOrder.getPaidAmount() != null && savedOrder.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            Payment initialPayment = new Payment();
            initialPayment.setOrderId(savedOrder.getId());
            initialPayment.setAmount(savedOrder.getPaidAmount());
            initialPayment.setPaymentMethod(savedOrder.getPaymentMethod());
            initialPayment.setChequeNumber(request.getChequeNumber());
            initialPayment.setProofFileUrl(request.getProofFileUrl());
            initialPayment.setRecordedBy(securityService.getCurrentUserId());
            initialPayment.setRecordedByName(securityService.getCurrentUserEmail());
            paymentRepository.save(initialPayment);
        }

        Commission commission = null;
        if (agent != null) {
            commission = commissionCalculationService.calculateCommissionForOrder(savedOrder);
        }

        auditLogService.record(AuditAction.CREATE, "Order", savedOrder.getId(),
                "Order " + savedOrder.getOrderNumber() + " created for "
                + (customer != null ? OrderItemSummaryBuilder.customerDisplayName(customer) : "walk-in")
                + " via " + savedOrder.getOrderSource());

        if (customer != null) {
            customerService.incrementTotalOrders(customer.getId());
        }

        orderNotificationService.sendOrderCreated(savedOrder, commission);

        eventPublisher.publishEvent(new OrderStatusChangedEvent(this, savedOrder, null, LocalDateTime.now()));

        return orderMapper.toResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    @Override
    public OrderResponse getOrderById(UUID id) {
        Order order = orderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));
        return orderMapper.toResponse(order);
    }

    @Transactional(readOnly = true)
    @Override
    public OrderResponse getOrderByOrderNumber(String orderNumber) {
        Order order = orderRepository.findByOrderNumberWithDetails(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderNumber", orderNumber));
        return orderMapper.toResponse(order);
    }

    @Transactional(readOnly = true)
    @Override
    public List<OrderResponse> getOrdersByAgentId(UUID agentId) {
        agentRepository.findById(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "id", agentId));
        List<Order> orders = orderRepository.findByAgentIdWithDetails(agentId);
        return orderMapper.toResponseList(orders);
    }

    @Transactional(readOnly = true)
    @Override
    public List<OrderResponse> getMyOrders() {
        UUID userId = securityService.getCurrentUserId();
        Agent agent = agentRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "userId", userId));
        List<Order> orders = orderRepository.findByAgentIdWithDetails(agent.getId());
        return orderMapper.toResponseList(orders);
    }

    @Transactional(readOnly = true)
    @Override
    public List<OrderResponse> getOrdersByCustomerId(UUID customerId) {
        customerService.getCustomerById(customerId);
        List<Order> orders = orderRepository.findByCustomerIdWithDetails(customerId);
        return orderMapper.toResponseList(orders);
    }

    @Transactional(readOnly = true)
    @Override
    public List<OrderResponse> getOrdersByCustomerId(UUID customerId, List<PaymentStatus> paymentStatuses) {
        List<OrderResponse> allOrders = getOrdersByCustomerId(customerId);
        if (paymentStatuses == null || paymentStatuses.isEmpty()) return allOrders;
        Set<PaymentStatus> statuses = EnumSet.copyOf(paymentStatuses);
        return allOrders.stream()
                .filter(o -> statuses.contains(o.getPaymentStatus()))
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<OrderResponse> getAllOrders() {
        List<Order> orders = orderRepository.findAllWithDetails();
        return orderMapper.toResponseList(orders);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<OrderResponse> getAllOrdersPaginated(Pageable pageable) {
        Page<Order> orders = orderRepository.findAllWithDetails(pageable);
        return orders.map(orderMapper::toResponse);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<OrderResponse> getOrdersByStatusPaginated(OrderStatus status, Pageable pageable) {
        Page<Order> orders = orderRepository.findByStatusWithDetails(status, pageable);
        return orders.map(orderMapper::toResponse);
    }

    @Transactional
    @Override
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

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(status);
        if (status == OrderStatus.SHIPPED && order.getDeliveryStatus() == null) {
            order.setDeliveryStatus(DeliveryStatus.PENDING);
        }
        Order updatedOrder = orderRepository.save(order);

        auditLogService.record(AuditAction.STATUS_CHANGE, "Order", updatedOrder.getId(),
                "Order " + updatedOrder.getOrderNumber() + " status changed from "
                + oldStatus + " to " + status);

        orderNotificationService.sendOrderStatus(updatedOrder);

        eventPublisher.publishEvent(new OrderStatusChangedEvent(this, updatedOrder, oldStatus, LocalDateTime.now()));

        return orderMapper.toResponse(updatedOrder);
    }

    @Transactional
    @Override
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

        if (!stockAlreadyRestored) {
            List<UUID> stockProductIds = order.getItems().stream()
                    .map(item -> item.getProduct().getId())
                    .toList();
            List<Integer> stockQuantities = order.getItems().stream()
                    .map(OrderItem::getQuantity)
                    .toList();
            List<String> stockNotes = order.getItems().stream()
                    .map(item -> "Order " + order.getOrderNumber() + " cancelled - Item restored")
                    .toList();
            inventoryService.recordOrderStocks(stockProductIds, stockQuantities, StockMovementType.RETURN, stockNotes);
        }

        commissionLifecycleService.cancelCommissionForOrder(id);

        BigDecimal previousOutstanding = order.getCustomer().getOutstandingBalance();
        BigDecimal previousAdvance = order.getCustomer().getAdvanceBalance();

        boolean hasPaidAmount = order.getPaidAmount().compareTo(BigDecimal.ZERO) > 0;
        DuesChangeType changeType;

        if (hasPaidAmount) {
            changeType = request.refundChoice() == RefundChoice.CREDIT_ADVANCE
                    ? DuesChangeType.CANCEL_ADVANCE
                    : DuesChangeType.CANCEL_REFUND;
            orderPaymentService.handleOrderSettlement(order, request.refundChoice(), changeType);
        } else {
            changeType = DuesChangeType.CANCEL_UNPAID;
            order.setOutstandingBalance(BigDecimal.ZERO);
        }

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        Order updatedOrder = orderRepository.save(order);

        orderPaymentService.syncCustomerOutstanding(order.getCustomer());
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
    @Override
    public OrderResponse returnOrder(UUID id, OrderSettlementRequest request) {
        Order order = orderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

        if (order.getStatus() == OrderStatus.RETURNED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("Cannot return an order that is already " + order.getStatus());
        }

        List<UUID> stockProductIds = order.getItems().stream()
                .map(item -> item.getProduct().getId())
                .toList();
        List<Integer> stockQuantities = order.getItems().stream()
                .map(OrderItem::getQuantity)
                .toList();
        List<String> stockNotes = order.getItems().stream()
                .map(item -> "Order " + order.getOrderNumber() + " returned - Item restored")
                .toList();
        inventoryService.recordOrderStocks(stockProductIds, stockQuantities, StockMovementType.RETURN, stockNotes);

        commissionLifecycleService.cancelCommissionForOrder(id);

        BigDecimal previousOutstanding = order.getCustomer().getOutstandingBalance();
        BigDecimal previousAdvance = order.getCustomer().getAdvanceBalance();

        boolean hasPaidAmount = order.getPaidAmount().compareTo(BigDecimal.ZERO) > 0;
        DuesChangeType changeType;

        if (hasPaidAmount) {
            changeType = request.refundChoice() == RefundChoice.CREDIT_ADVANCE
                    ? DuesChangeType.RETURN_ADVANCE
                    : DuesChangeType.RETURN_REFUND;
            orderPaymentService.handleOrderSettlement(order, request.refundChoice(), changeType);
        } else {
            changeType = DuesChangeType.CANCEL_UNPAID;
            order.setOutstandingBalance(BigDecimal.ZERO);
        }

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(OrderStatus.RETURNED);
        Order updatedOrder = orderRepository.save(order);

        ledgerService.recordCustomerRefund(
                order.getCustomer().getId(),
                updatedOrder.getId(),
                order.getPaidAmount(),
                "Return for Order " + order.getOrderNumber(),
                securityService.getCurrentUserId()
        );

        orderPaymentService.syncCustomerOutstanding(order.getCustomer());
        customerRepository.save(order.getCustomer());

        eventPublisher.publishEvent(new CustomerDuesChangedEvent(
                this, order.getCustomer(),
                previousOutstanding, order.getCustomer().getOutstandingBalance(),
                previousAdvance, order.getCustomer().getAdvanceBalance(),
                changeType
        ));

        auditLogService.record(AuditAction.STATUS_CHANGE, "Order", updatedOrder.getId(),
                "Order " + updatedOrder.getOrderNumber() + " returned");

        orderNotificationService.sendOrderStatus(updatedOrder);

        eventPublisher.publishEvent(new OrderStatusChangedEvent(this, updatedOrder, oldStatus, LocalDateTime.now()));

        return orderMapper.toResponse(updatedOrder);
    }

    @Transactional(readOnly = true)
    @Override
    public byte[] generateInvoicePdf(UUID id) {
        return orderPdfService.generateInvoicePdf(id);
    }

    @Transactional(readOnly = true)
    @Override
    public byte[] generateReceiptPdf(UUID id) {
        return orderPdfService.generateReceiptPdf(id);
    }

    @Transactional(readOnly = true)
    @Override
    public byte[] generateShippingLabelPdf(UUID id) {
        return orderPdfService.generateShippingLabelPdf(id);
    }

    @Transactional
    @Override
    public OrderResponse recordPayment(UUID id, RecordPaymentRequest request) {
        return orderPaymentService.recordPayment(id, request);
    }

    @Override
    public List<PaymentResponse> getPaymentHistory(UUID orderId) {
        return orderPaymentService.getPaymentHistory(orderId);
    }

    @Transactional
    @Override
    public OrderResponse useAdvance(UUID orderId, UseAdvanceRequest request) {
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        Customer customer = order.getCustomer();
        if (customer == null) {
            throw new IllegalArgumentException("Order has no customer assigned");
        }

        BigDecimal previousOutstanding = customer.getOutstandingBalance();
        BigDecimal previousAdvance = customer.getAdvanceBalance();

        orderPaymentService.useAdvanceForOrder(order, request.amount());

        Order updatedOrder = orderRepository.save(order);
        customerRepository.save(customer);

        eventPublisher.publishEvent(new CustomerDuesChangedEvent(
                this, customer,
                previousOutstanding, customer.getOutstandingBalance(),
                previousAdvance, customer.getAdvanceBalance(),
                DuesChangeType.ADVANCE_USED
        ));

        auditLogService.record(AuditAction.UPDATE, "Order", order.getId(),
                "Used advance " + request.amount() + " for order " + order.getOrderNumber());

        return orderMapper.toResponse(updatedOrder);
    }

    @Recover
    public OrderResponse recoverDuplicateOrderNumber(DuplicateOrderNumberException ex, OrderRequest request) {
        log.error("Failed to create order after retries: {}", ex.getMessage());
        throw new IllegalStateException("Couldn't generate order number. Try again.", ex);
    }

    private static boolean isDuplicateOrderNumber(DataIntegrityViolationException ex) {
        Throwable cause = ex.getMostSpecificCause();
        if (cause == null) return false;
        String msg = cause.getMessage();
        return msg != null && (msg.contains("uk_orders_order_number") || msg.contains("orders_order_number_key"));
    }
}
