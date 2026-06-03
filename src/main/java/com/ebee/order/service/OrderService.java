package com.ebee.order.service;

import com.ebee.agent.entity.Agent;
import com.ebee.agent.repository.AgentRepository;
import com.ebee.commission.service.CommissionCalculationService;
import com.ebee.commission.service.CommissionLifecycleService;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.security.SecurityService;
import com.ebee.customer.entity.Customer;
import com.ebee.customer.service.CustomerService;
import com.ebee.inventory.service.InventoryService;
import com.ebee.commission.entity.Commission;
import com.ebee.notification.NotificationService;
import com.ebee.order.dto.OrderItemRequest;
import com.ebee.order.dto.OrderRequest;
import com.ebee.order.dto.OrderResponse;
import com.ebee.order.entity.Order;
import com.ebee.order.entity.OrderItem;
import com.ebee.order.enums.OrderStatus;
import com.ebee.order.mapper.OrderMapper;
import com.ebee.order.repository.OrderRepository;
import com.ebee.product.entity.Product;
import com.ebee.product.repository.ProductRepository;
import com.ebee.stock.enums.StockMovementType;
import com.ebee.user.entity.User;
import com.ebee.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

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
    private final NotificationService notificationService;

    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        Agent agent = null;
        
        if (request.getAgentCode() != null) {
            agent = agentRepository.findByAgentCode(request.getAgentCode())
                    .orElseThrow(() -> new ResourceNotFoundException("Agent", "agentCode", request.getAgentCode()));

            if (!Boolean.TRUE.equals(agent.getIsActive())) {
                throw new IllegalArgumentException("Agent is not active");
            }
        } else if (!securityService.isAdmin()) {
            throw new IllegalArgumentException("Agent is required for non-admin users");
        }

        User currentUser = userRepository.findById(securityService.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", securityService.getCurrentUserId()));

        Customer customer = customerService.findByPhone(request.getCustomerPhoneNumber());

        String orderNumber = orderNumberService.generateOrderNumber();

        Order order = Order.builder()
                .agent(agent)
                .customer(customer)
                .createdBy(currentUser)
                .orderNumber(orderNumber)
                .notes(request.getNotes())
                .status(OrderStatus.PENDING)
                .items(new ArrayList<>())
                .build();

        BigDecimal totalAmount = BigDecimal.ZERO;
        AtomicInteger sequence = new AtomicInteger(1);

        for (OrderItemRequest itemRequest : request.getItems()) {
            Product product = productRepository.findByItemCode(itemRequest.getProductItemCode())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "itemCode", itemRequest.getProductItemCode()));

            if (!Boolean.TRUE.equals(product.getIsActive())) {
                throw new IllegalArgumentException("Product is not active: " + product.getName());
            }

            if (!inventoryService.hasSufficientStock(product.getId(), itemRequest.getQuantity())) {
                Integer available = inventoryService.getAvailableStock(product.getId());
                throw new IllegalArgumentException("Insufficient stock for " + product.getName() + ". Available: " + available);
            }

            BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(itemRequest.getQuantity()));

            OrderItem item = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(product.getPrice())
                    .subtotal(subtotal)
                    .build();

            order.getItems().add(item);
            totalAmount = totalAmount.add(subtotal);

            inventoryService.recordOrderStock(
                    product.getId(),
                    itemRequest.getQuantity(),
                    StockMovementType.OUT,
                    "Order " + orderNumber + " - Item " + sequence.getAndIncrement()
            );
        }

        order.setTotalAmount(totalAmount);
        Order savedOrder = orderRepository.save(order);

        Commission commission = null;
        if (agent != null) {
            commission = commissionCalculationService.calculateCommissionForOrder(savedOrder);
        }

        if (customer != null) {
            customerService.incrementTotalOrders(customer.getId());
        }

        sendOrderCreatedNotifications(savedOrder, commission);

        return orderMapper.toResponse(savedOrder);
    }

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
    public List<OrderResponse> getOrdersByCustomerId(UUID customerId) {
        customerService.getCustomerById(customerId);
        List<Order> orders = orderRepository.findByCustomerIdWithDetails(customerId);
        return orderMapper.toResponseList(orders);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        List<Order> orders = orderRepository.findAllWithDetails();
        return orderMapper.toResponseList(orders);
    }

    @Transactional
    public OrderResponse updateOrderStatus(UUID id, OrderStatus status) {
        Order order = orderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.DELIVERED) {
            throw new IllegalArgumentException("Cannot update status of a " + order.getStatus().name().toLowerCase() + " order");
        }

        order.setStatus(status);
        Order updatedOrder = orderRepository.save(order);

        sendOrderStatusNotifications(updatedOrder);

        return orderMapper.toResponse(updatedOrder);
    }

    @Transactional
    public OrderResponse cancelOrder(UUID id) {
        Order order = orderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("Order is already cancelled");
        }

        if (order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.DELIVERED) {
            throw new IllegalArgumentException("Cannot cancel a " + order.getStatus().name().toLowerCase() + " order");
        }

        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();

            inventoryService.recordOrderStock(
                    product.getId(),
                    item.getQuantity(),
                    StockMovementType.RETURN,
                    "Order " + order.getOrderNumber() + " cancelled - Item restored"
            );
        }

        commissionLifecycleService.cancelCommissionForOrder(id);

        order.setStatus(OrderStatus.CANCELLED);
        Order updatedOrder = orderRepository.save(order);

        sendOrderCancelledNotifications(updatedOrder);

        return orderMapper.toResponse(updatedOrder);
    }

    private String buildItemSummary(Order order) {
        return order.getItems().stream()
                .map(item -> item.getProduct().getName() + " (x" + item.getQuantity() + ")")
                .collect(Collectors.joining(", "));
    }

    private String customerDisplayName(Customer customer) {
        return customer.getFirstName() + " " + customer.getLastName();
    }

    private void sendOrderCreatedNotifications(Order order, Commission commission) {
        String itemSummary = buildItemSummary(order);

        if (order.getCustomer() != null) {
            String customerSubject = "Order Placed – " + order.getOrderNumber();
            String customerMessage = """
                    Dear %s,
                    
                    Your order %s has been placed successfully.
                    
                    Items: %s
                    Total: LKR %.2f
                    
                    We will notify you once it's confirmed.
                    Thank you for choosing Ebee!
                    """.formatted(
                    customerDisplayName(order.getCustomer()),
                    order.getOrderNumber(),
                    itemSummary,
                    order.getTotalAmount()
            );
            notificationService.notifyCustomer(order.getCustomer(), customerSubject, customerMessage);
        }

        if (order.getAgent() != null) {
            String agentSubject = "New Order Assigned – " + order.getOrderNumber();
            String customerInfo = order.getCustomer() != null
                    ? "Customer: " + customerDisplayName(order.getCustomer())
                    : "Customer: Walk-in";
            String commissionInfo = commission != null
                    ? "Commission: LKR %.2f (%.2f%% rate)".formatted(
                            commission.getCommissionAmount(), commission.getCommissionRate())
                    : "";
            String agentMessage = """
                    Dear %s,
                    
                    A new order %s has been assigned to you.
                    
                    %s
                    Items: %s
                    Total: LKR %.2f
                    %s
                    
                    Please process the order at your earliest convenience.
                    """.formatted(
                    order.getAgent().getUser().getFirstName(),
                    order.getOrderNumber(),
                    customerInfo,
                    itemSummary,
                    order.getTotalAmount(),
                    commissionInfo
            );
            notificationService.notifyAgent(order.getAgent(), agentSubject, agentMessage);
        }
    }

    private void sendOrderStatusNotifications(Order order) {
        if (order.getCustomer() != null) {
            String statusMessage = switch (order.getStatus()) {
                case CONFIRMED -> "has been confirmed and is being prepared.";
                case PROCESSING -> "is now being processed.";
                case SHIPPED -> "has been shipped and is on its way!";
                case DELIVERED -> "has been delivered. Thank you for shopping with us!";
                default -> null;
            };

            if (statusMessage != null) {
                String subject = "Order " + order.getStatus().name() + " – " + order.getOrderNumber();
                String message = "Dear %s,\n\nYour order %s %s".formatted(
                        customerDisplayName(order.getCustomer()),
                        order.getOrderNumber(),
                        statusMessage
                );
                notificationService.notifyCustomer(order.getCustomer(), subject, message);
            }
        }

        if (order.getAgent() != null) {
            String statusMessage = switch (order.getStatus()) {
                case CONFIRMED -> "has been confirmed.";
                case PROCESSING -> "is now being processed.";
                case SHIPPED -> "has been shipped.";
                case DELIVERED -> "has been delivered.";
                default -> null;
            };

            if (statusMessage != null) {
                String customerInfo = order.getCustomer() != null
                        ? customerDisplayName(order.getCustomer())
                        : "Walk-in";
                String subject = "Order " + order.getStatus().name() + " – " + order.getOrderNumber();
                String message = "Dear %s,\n\nOrder %s for %s %s".formatted(
                        order.getAgent().getUser().getFirstName(),
                        order.getOrderNumber(),
                        customerInfo,
                        statusMessage
                );
                notificationService.notifyAgent(order.getAgent(), subject, message);
            }
        }
    }

    private void sendOrderCancelledNotifications(Order order) {
        if (order.getCustomer() != null) {
            String subject = "Order Cancelled – " + order.getOrderNumber();
            String message = """
                    Dear %s,
                    
                    Your order %s has been cancelled.
                    
                    If you need assistance, please contact our support team.
                    """.formatted(
                    customerDisplayName(order.getCustomer()),
                    order.getOrderNumber()
            );
            notificationService.notifyCustomer(order.getCustomer(), subject, message);
        }

        if (order.getAgent() != null) {
            String customerInfo = order.getCustomer() != null
                    ? customerDisplayName(order.getCustomer())
                    : "Walk-in";
            String subject = "Order Cancelled – " + order.getOrderNumber();
            String message = "Dear %s,\n\nOrder %s for %s has been cancelled.".formatted(
                    order.getAgent().getUser().getFirstName(),
                    order.getOrderNumber(),
                    customerInfo
            );
            notificationService.notifyAgent(order.getAgent(), subject, message);
        }
    }
}
