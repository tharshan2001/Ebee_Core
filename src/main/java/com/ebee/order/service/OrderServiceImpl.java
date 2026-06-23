package com.ebee.order.service;

import com.ebee.agent.entity.Agent;
import com.ebee.agent.repository.AgentRepository;
import com.ebee.audit.enums.AuditAction;
import com.ebee.audit.service.AuditLogService;
import com.ebee.commission.service.CommissionCalculationService;
import com.ebee.commission.service.CommissionLifecycleService;
import com.ebee.common.enums.District;
import com.ebee.common.service.PrintTemplateService;
import com.ebee.order.dto.InvoiceData;
import com.ebee.order.dto.ShippingLabelData;
import com.ebee.order.exception.DuplicateOrderNumberException;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.security.SecurityService;
import com.ebee.order.util.OrderItemSummaryBuilder;
import com.ebee.customer.entity.Customer;
import com.ebee.customer.service.CustomerService;
import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.inventory.service.InventoryService;
import com.ebee.commission.entity.Commission;
import com.ebee.order.dto.OrderItemRequest;
import com.ebee.order.dto.OrderRequest;
import com.ebee.order.dto.OrderResponse;
import com.ebee.order.entity.Order;
import com.ebee.order.entity.OrderItem;
import com.ebee.order.enums.OrderSource;
import com.ebee.order.enums.OrderStatus;
import com.ebee.order.mapper.OrderMapper;
import com.ebee.order.repository.OrderRepository;
import com.ebee.product.entity.Product;
import com.ebee.product.repository.ProductRepository;
import com.ebee.stock.enums.StockMovementType;
import com.ebee.user.entity.User;
import com.ebee.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final CustomerService customerService;
    private final OrderNotificationService orderNotificationService;
    private final AuditLogService auditLogService;
    private final PrintTemplateService printTemplateService;

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
                    "Customer '" + customer.getPhoneNumber() + "' has no district on file; "
                            + "update the customer profile before placing an order");
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
                .items(new ArrayList<>())
                .build();

        List<String> itemCodes = request.getItems().stream()
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

        List<UUID> stockProductIds = new ArrayList<>();
        List<Integer> stockQuantities = new ArrayList<>();
        List<String> stockNotes = new ArrayList<>();

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (int i = 0; i < request.getItems().size(); i++) {
            OrderItemRequest itemRequest = request.getItems().get(i);
            Product product = productMap.get(itemRequest.getProductItemCode());

            // Admin can apply an optional per-item discount at order time, capped
            // by the product's maxDiscount (set at product creation/update).
            // getPriceWithDiscount() throws IllegalArgumentException if the
            // discount exceeds the cap, so we let it bubble up to a 400.
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

        inventoryService.recordOrderStocks(stockProductIds, stockQuantities, StockMovementType.OUT, stockNotes);

        order.setTotalAmount(totalAmount);
        Order savedOrder;
        try {
            savedOrder = orderRepository.save(order);
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateOrderNumber(ex)) {
                throw new DuplicateOrderNumberException("Duplicate order number: " + order.getOrderNumber(), ex);
            }
            throw ex;
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
            throw new IllegalArgumentException("Order status can only be updated up to SHIPPED. Use delivery or cancel endpoints for further changes.");
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

        return orderMapper.toResponse(updatedOrder);
    }

    @Transactional
    @Override
    public OrderResponse cancelOrder(UUID id) {
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

        // If the delivery has already been marked FAILED_PERMANENT, stock was
        // already restored by DeliveryService. Skip the restoration here to
        // avoid double-adding stock (which would let a customer inflate
        // inventory by repeatedly cancelling and re-ordering).
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

        order.setStatus(OrderStatus.CANCELLED);
        Order updatedOrder = orderRepository.save(order);

        auditLogService.record(AuditAction.STATUS_CHANGE, "Order", updatedOrder.getId(),
                "Order " + updatedOrder.getOrderNumber() + " cancelled");

        orderNotificationService.sendOrderCancelled(updatedOrder);

        return orderMapper.toResponse(updatedOrder);
    }

    @Transactional(readOnly = true)
    @Override
    public byte[] generateInvoicePdf(UUID id) {
        Order order = orderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

        String address = null;
        if (order.getCustomer() != null && order.getCustomer().getAddress() != null) {
            var a = order.getCustomer().getAddress();
            address = a.getStreet() + ", " + a.getCity() + ", " + a.getDistrict().getDisplayName() + " - " + a.getPostalCode();
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discountTotal = BigDecimal.ZERO;
        var lineItems = new java.util.ArrayList<InvoiceData.LineItem>();
        for (var item : order.getItems()) {
            BigDecimal itemSubtotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            BigDecimal discount = BigDecimal.ZERO;
            if (item.getOriginalUnitPrice() != null && item.getOriginalUnitPrice().compareTo(item.getUnitPrice()) > 0) {
                discount = item.getOriginalUnitPrice().subtract(item.getUnitPrice()).multiply(BigDecimal.valueOf(item.getQuantity()));
            }
            subtotal = subtotal.add(itemSubtotal);
            discountTotal = discountTotal.add(discount);
            lineItems.add(new InvoiceData.LineItem(
                    item.getProduct().getName(),
                    item.getProduct().getItemCode(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    itemSubtotal,
                    discount.compareTo(BigDecimal.ZERO) > 0 ? discount.toString() : null
            ));
        }

        var data = new InvoiceData(
                "INVOICE",
                order.getOrderNumber(),
                order.getCreatedAt(),
                order.getCustomer() != null ? order.getCustomer().getFirstName() + " " + order.getCustomer().getLastName() : null,
                order.getCustomer() != null ? order.getCustomer().getPhoneNumber() : null,
                address,
                order.getAgent() != null ? order.getAgent().getUser().getFirstName() + " " + order.getAgent().getUser().getLastName() : null,
                order.getAgent() != null ? order.getAgent().getAgentCode() : null,
                lineItems,
                subtotal,
                discountTotal,
                order.getTotalAmount(),
                order.getNotes()
        );

        return printTemplateService.renderToPdf("invoice", toMap(data));
    }

    @Transactional(readOnly = true)
    @Override
    public byte[] generateReceiptPdf(UUID id) {
        Order order = orderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

        String address = null;
        if (order.getCustomer() != null && order.getCustomer().getAddress() != null) {
            var a = order.getCustomer().getAddress();
            address = a.getStreet() + ", " + a.getCity() + ", " + a.getDistrict().getDisplayName() + " - " + a.getPostalCode();
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discountTotal = BigDecimal.ZERO;
        var lineItems = new java.util.ArrayList<InvoiceData.LineItem>();
        for (var item : order.getItems()) {
            BigDecimal itemSubtotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            BigDecimal discount = BigDecimal.ZERO;
            if (item.getOriginalUnitPrice() != null && item.getOriginalUnitPrice().compareTo(item.getUnitPrice()) > 0) {
                discount = item.getOriginalUnitPrice().subtract(item.getUnitPrice()).multiply(BigDecimal.valueOf(item.getQuantity()));
            }
            subtotal = subtotal.add(itemSubtotal);
            discountTotal = discountTotal.add(discount);
            lineItems.add(new InvoiceData.LineItem(
                    item.getProduct().getName(),
                    item.getProduct().getItemCode(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    itemSubtotal,
                    discount.compareTo(BigDecimal.ZERO) > 0 ? discount.toString() : null
            ));
        }

        var data = new com.ebee.order.dto.ReceiptData(
                "RECEIPT",
                order.getOrderNumber(),
                order.getCreatedAt(),
                java.time.LocalDateTime.now(),
                order.getCustomer() != null ? order.getCustomer().getFirstName() + " " + order.getCustomer().getLastName() : null,
                order.getCustomer() != null ? order.getCustomer().getPhoneNumber() : null,
                address,
                order.getAgent() != null ? order.getAgent().getUser().getFirstName() + " " + order.getAgent().getUser().getLastName() : null,
                order.getAgent() != null ? order.getAgent().getAgentCode() : null,
                "Cash",
                lineItems,
                subtotal,
                discountTotal,
                order.getTotalAmount(),
                order.getNotes()
        );

        return printTemplateService.renderToPdf("receipt", toMap(data));
    }

    @Transactional(readOnly = true)
    @Override
    public byte[] generateShippingLabelPdf(UUID id) {
        Order order = orderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

        String address = null;
        if (order.getCustomer() != null && order.getCustomer().getAddress() != null) {
            var a = order.getCustomer().getAddress();
            address = a.getStreet() + ", " + a.getCity() + ", " + a.getDistrict().getDisplayName() + " - " + a.getPostalCode();
        }

        var data = new ShippingLabelData(
                order.getOrderNumber(),
                order.getCustomer() != null ? order.getCustomer().getFirstName() + " " + order.getCustomer().getLastName() : null,
                order.getCustomer() != null ? order.getCustomer().getPhoneNumber() : null,
                address,
                order.getItems().size(),
                order.getDeliveryNotes()
        );

        return printTemplateService.renderToPdf("shipping-label", toMap(data));
    }

    private java.util.Map<String, Object> toMap(Object record) {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        try {
            for (var method : record.getClass().getRecordComponents()) {
                map.put(method.getName(), method.getAccessor().invoke(record));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert record to map", e);
        }
        return map;
    }

    @Recover
    public OrderResponse recoverDuplicateOrderNumber(DuplicateOrderNumberException ex, OrderRequest request) {
        log.error("Failed to create order after retries: {}", ex.getMessage());
        throw new IllegalStateException("Unable to generate unique order number. Please try again.", ex);
    }

    /**
     * Check whether a {@link DataIntegrityViolationException} was
     * caused by the {@code uk_orders_order_number} unique constraint.
     * The precise constraint name is database-specific (PostgreSQL
     * reports it via the SQL exception chain), so we compare against
     * the most specific substring we control.
     */
    private static boolean isDuplicateOrderNumber(DataIntegrityViolationException ex) {
        Throwable cause = ex.getMostSpecificCause();
        if (cause == null) return false;
        String msg = cause.getMessage();
        return msg != null && (msg.contains("uk_orders_order_number") || msg.contains("orders_order_number_key"));
    }

}
