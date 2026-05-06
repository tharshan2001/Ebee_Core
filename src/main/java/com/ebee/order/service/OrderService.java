package com.ebee.order.service;

import com.ebee.agent.entity.Agent;
import com.ebee.agent.repository.AgentRepository;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.security.SecurityService;
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
import com.ebee.stock.dto.StockRequest;
import com.ebee.stock.enums.StockMovementType;
import com.ebee.stock.service.StockService;
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

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final AgentRepository agentRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderMapper orderMapper;
    private final SecurityService securityService;
    private final StockService stockService;
    private final OrderNumberService orderNumberService;

    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        Agent agent = agentRepository.findById(request.getAgentId())
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "id", request.getAgentId()));

        if (!Boolean.TRUE.equals(agent.getIsActive())) {
            throw new IllegalArgumentException("Agent is not active");
        }

        User currentUser = userRepository.findById(securityService.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", securityService.getCurrentUserId()));

        String orderNumber = orderNumberService.generateOrderNumber();

        Order order = Order.builder()
                .agent(agent)
                .createdBy(currentUser)
                .orderNumber(orderNumber)
                .notes(request.getNotes())
                .status(OrderStatus.PENDING)
                .items(new ArrayList<>())
                .build();

        BigDecimal totalAmount = BigDecimal.ZERO;
        AtomicInteger sequence = new AtomicInteger(1);

        for (OrderItemRequest itemRequest : request.getItems()) {
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "id", itemRequest.getProductId()));

            if (!Boolean.TRUE.equals(product.getIsActive())) {
                throw new IllegalArgumentException("Product is not active: " + product.getName());
            }

            int currentStock = product.getQuantityInStock();
            if (currentStock < itemRequest.getQuantity()) {
                throw new IllegalArgumentException("Insufficient stock for " + product.getName() + ". Available: " + currentStock);
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

            // Update stock through StockService
            StockRequest stockRequest = new StockRequest();
            stockRequest.setProductId(itemRequest.getProductId());
            stockRequest.setQuantityChange(-itemRequest.getQuantity());
            stockRequest.setMovementType(StockMovementType.OUT);
            stockRequest.setNotes("Order " + orderNumber + " - Item " + sequence.getAndIncrement());
            stockService.recordStockMovement(stockRequest);
        }

        order.setTotalAmount(totalAmount);
        Order savedOrder = orderRepository.save(order);
        return orderMapper.toResponse(savedOrder);
    }

    public OrderResponse getOrderById(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));
        return orderMapper.toResponse(order);
    }

    public OrderResponse getOrderByOrderNumber(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderNumber", orderNumber));
        return orderMapper.toResponse(order);
    }

    public List<OrderResponse> getOrdersByAgentId(UUID agentId) {
        agentRepository.findById(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "id", agentId));
        List<Order> orders = orderRepository.findByAgentId(agentId);
        return orderMapper.toResponseList(orders);
    }

    public List<OrderResponse> getAllOrders() {
        List<Order> orders = orderRepository.findAll();
        return orderMapper.toResponseList(orders);
    }

    @Transactional
    public OrderResponse updateOrderStatus(UUID id, OrderStatus status) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.DELIVERED) {
            throw new IllegalArgumentException("Cannot update status of a " + order.getStatus().name().toLowerCase() + " order");
        }

        order.setStatus(status);
        Order updatedOrder = orderRepository.save(order);
        return orderMapper.toResponse(updatedOrder);
    }

    @Transactional
    public OrderResponse cancelOrder(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("Order is already cancelled");
        }

        if (order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.DELIVERED) {
            throw new IllegalArgumentException("Cannot cancel a " + order.getStatus().name().toLowerCase() + " order");
        }

        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();

            StockRequest stockRequest = new StockRequest();
            stockRequest.setProductId(product.getId());
            stockRequest.setQuantityChange(item.getQuantity());
            stockRequest.setMovementType(StockMovementType.RETURN);
            stockRequest.setNotes("Order " + order.getOrderNumber() + " cancelled - Item restored");
            stockService.recordStockMovement(stockRequest);
        }

        order.setStatus(OrderStatus.CANCELLED);
        Order updatedOrder = orderRepository.save(order);
        return orderMapper.toResponse(updatedOrder);
    }
}
