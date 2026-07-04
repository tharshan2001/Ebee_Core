package com.ebee.order.service;

import com.ebee.agent.entity.Agent;
import com.ebee.agent.repository.AgentRepository;
import com.ebee.audit.enums.AuditAction;
import com.ebee.audit.service.AuditLogService;
import com.ebee.commission.entity.Commission;
import com.ebee.commission.enums.CommissionStatus;
import com.ebee.commission.service.CommissionCalculationService;
import com.ebee.commission.service.CommissionLifecycleService;
import com.ebee.common.enums.District;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.security.SecurityService;
import com.ebee.customer.entity.Address;
import com.ebee.customer.entity.Customer;
import com.ebee.customer.service.CustomerService;
import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.inventory.service.InventoryService;
import com.ebee.order.dto.OrderItemRequest;
import com.ebee.order.dto.OrderRequest;
import com.ebee.order.dto.OrderResponse;
import com.ebee.order.entity.Order;
import com.ebee.order.entity.OrderItem;
import com.ebee.order.enums.OrderSource;
import com.ebee.order.enums.OrderStatus;
import com.ebee.order.enums.PaymentMethod;
import com.ebee.order.enums.PaymentStatus;
import com.ebee.order.mapper.OrderMapper;
import com.ebee.order.repository.OrderRepository;
import com.ebee.product.entity.Product;
import com.ebee.product.repository.ProductRepository;
import com.ebee.stock.enums.StockMovementType;
import com.ebee.user.entity.User;
import com.ebee.user.enums.RoleType;
import com.ebee.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private AgentRepository agentRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrderMapper orderMapper;
    @Mock private SecurityService securityService;
    @Mock private InventoryService inventoryService;
    @Mock private OrderNumberService orderNumberService;
    @Mock private CommissionCalculationService commissionCalculationService;
    @Mock private CommissionLifecycleService commissionLifecycleService;
    @Mock private CustomerService customerService;
    @Mock private OrderNotificationService orderNotificationService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private OrderServiceImpl orderService;

    @Captor private ArgumentCaptor<Order> orderCaptor;

    private UUID adminUserId;
    private UUID agentUserId;
    private User adminUser;
    private User agentUser;
    private Agent agent;
    private Customer customer;
    private Product product1;
    private Product product2;
    private OrderRequest orderRequest;
    private Order order;

    @BeforeEach
    void setUp() {
        adminUserId = UUID.randomUUID();
        agentUserId = UUID.randomUUID();

        adminUser = User.builder()
                .id(adminUserId)
                .email("admin@ebee.com")
                .firstName("Admin")
                .lastName("User")
                .role(RoleType.ADMIN)
                .build();

        agentUser = User.builder()
                .id(agentUserId)
                .email("agent@ebee.com")
                .firstName("Agent")
                .lastName("User")
                .role(RoleType.AGENT)
                .build();

        agent = Agent.builder()
                .id(UUID.randomUUID())
                .user(agentUser)
                .agentCode("JF-008")
                .commissionRate(BigDecimal.valueOf(7.5))
                .isActive(true)
                .build();

        Address address = Address.builder()
                .id(UUID.randomUUID())
                .street("123 Main St")
                .city("Jaffna")
                .district(District.JF)
                .build();

        customer = Customer.builder()
                .id(UUID.randomUUID())
                .firstName("John")
                .lastName("Doe")
                .phoneNumber("0771234567")
                .address(address)
                .build();

        product1 = Product.builder()
                .id(UUID.randomUUID())
                .itemCode("PROD-001")
                .name("Product One")
                .price(new BigDecimal("2500.00"))
                .isActive(true)
                .build();

        product2 = Product.builder()
                .id(UUID.randomUUID())
                .itemCode("PROD-002")
                .name("Product Two")
                .price(new BigDecimal("1500.00"))
                .isActive(true)
                .build();

        OrderItemRequest item1 = new OrderItemRequest();
        item1.setProductItemCode("PROD-001");
        item1.setQuantity(2);

        OrderItemRequest item2 = new OrderItemRequest();
        item2.setProductItemCode("PROD-002");
        item2.setQuantity(1);

        orderRequest = new OrderRequest();
        orderRequest.setAgentCode("JF-008");
        orderRequest.setCustomerPhoneNumber("0771234567");
        orderRequest.setPaymentMethod(PaymentMethod.CASH);
        orderRequest.setNotes("Test order");
        orderRequest.setItems(List.of(item1, item2));

        order = Order.builder()
                .id(UUID.randomUUID())
                .agent(agent)
                .customer(customer)
                .createdBy(adminUser)
                .orderNumber("JF-A-20250614-001")
                .notes("Test order")
                .status(OrderStatus.PENDING)
                .orderSource(OrderSource.AGENT)
                .items(List.of(
                        OrderItem.builder()
                                .product(product1)
                                .quantity(2)
                                .unitPrice(new BigDecimal("2500.00"))
                                .subtotal(new BigDecimal("5000.00"))
                                .build(),
                        OrderItem.builder()
                                .product(product2)
                                .quantity(1)
                                .unitPrice(new BigDecimal("1500.00"))
                                .subtotal(new BigDecimal("1500.00"))
                                .build()
                ))
                .totalAmount(new BigDecimal("6500.00"))
                .build();
    }

    @Test
    void createOrder_asAdminWithAgentCode_createsOrderSuccessfully() {
        when(securityService.isAdmin()).thenReturn(true);
        when(agentRepository.findByAgentCode("JF-008")).thenReturn(Optional.of(agent));
        when(securityService.getCurrentUserId()).thenReturn(adminUserId);
        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));
        when(customerService.findByPhone("0771234567")).thenReturn(customer);
        when(orderNumberService.generateOrderNumber(District.JF, OrderSource.AGENT))
                .thenReturn("JF-A-20250614-001");
        when(productRepository.findByItemCodeIn(anyList()))
                .thenReturn(List.of(product1, product2));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(commissionCalculationService.calculateCommissionForOrder(any()))
                .thenReturn(Commission.builder()
                        .id(UUID.randomUUID())
                        .commissionAmount(new BigDecimal("487.50"))
                        .commissionRate(new BigDecimal("7.5"))
                        .status(CommissionStatus.CALCULATED)
                        .build());
        when(orderMapper.toResponse(any())).thenReturn(new OrderResponse());

        var result = orderService.createOrder(orderRequest);

        assertThat(result).isNotNull();
        verify(inventoryService).recordOrderStocks(
                anyList(), anyList(), eq(StockMovementType.OUT), anyList());
        verify(commissionCalculationService).calculateCommissionForOrder(any());
        verify(auditLogService).record(eq(AuditAction.CREATE), eq("Order"), any(), anyString());
        verify(customerService).incrementTotalOrders(customer.getId());
        verify(orderNotificationService).sendOrderCreated(any(), any());
    }

    @Test
    void createOrder_asAgent_createsOrderSuccessfully() {
        orderRequest.setAgentCode(null);

        when(securityService.isAdmin()).thenReturn(false);
        when(securityService.getCurrentUserId()).thenReturn(agentUserId);
        when(agentRepository.findByUserId(agentUserId)).thenReturn(Optional.of(agent));
        when(userRepository.findById(agentUserId)).thenReturn(Optional.of(agentUser));
        when(customerService.findByPhone("0771234567")).thenReturn(customer);
        when(orderNumberService.generateOrderNumber(District.JF, OrderSource.AGENT))
                .thenReturn("JF-A-20250614-001");
        when(productRepository.findByItemCodeIn(anyList()))
                .thenReturn(List.of(product1, product2));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(commissionCalculationService.calculateCommissionForOrder(any()))
                .thenReturn(Commission.builder()
                        .id(UUID.randomUUID())
                        .commissionAmount(new BigDecimal("487.50"))
                        .status(CommissionStatus.CALCULATED)
                        .build());
        when(orderMapper.toResponse(any())).thenReturn(new OrderResponse());

        var result = orderService.createOrder(orderRequest);

        assertThat(result).isNotNull();
        verify(agentRepository).findByUserId(agentUserId);
    }

    @Test
    void createOrder_inactiveAgent_throwsIllegalArgument() {
        agent.setIsActive(false);

        when(securityService.isAdmin()).thenReturn(true);
        when(agentRepository.findByAgentCode("JF-008")).thenReturn(Optional.of(agent));

        assertThatThrownBy(() -> orderService.createOrder(orderRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Agent is not active");
    }

    @Test
    void createOrder_productNotFound_throwsResourceNotFound() {
        when(securityService.isAdmin()).thenReturn(true);
        when(agentRepository.findByAgentCode("JF-008")).thenReturn(Optional.of(agent));
        when(securityService.getCurrentUserId()).thenReturn(adminUserId);
        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));
        when(customerService.findByPhone("0771234567")).thenReturn(customer);
        when(orderNumberService.generateOrderNumber(District.JF, OrderSource.AGENT))
                .thenReturn("JF-A-20250614-001");
        when(productRepository.findByItemCodeIn(anyList()))
                .thenReturn(List.of(product1));

        assertThatThrownBy(() -> orderService.createOrder(orderRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product");
    }

    @Test
    void createOrder_inactiveProduct_throwsIllegalArgument() {
        product2.setIsActive(false);

        when(securityService.isAdmin()).thenReturn(true);
        when(agentRepository.findByAgentCode("JF-008")).thenReturn(Optional.of(agent));
        when(securityService.getCurrentUserId()).thenReturn(adminUserId);
        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));
        when(customerService.findByPhone("0771234567")).thenReturn(customer);
        when(orderNumberService.generateOrderNumber(District.JF, OrderSource.AGENT))
                .thenReturn("JF-A-20250614-001");
        when(productRepository.findByItemCodeIn(anyList()))
                .thenReturn(List.of(product1, product2));

        assertThatThrownBy(() -> orderService.createOrder(orderRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product is not active");
    }

    @Test
    void createOrder_customerWithoutAddress_throwsIllegalArgument() {
        // Customer with no address on file -> reject with a clear message
        // rather than letting customer.getAddress().getDistrict() NPE.
        Customer customerNoAddress = Customer.builder()
                .id(customer.getId())
                .firstName(customer.getFirstName())
                .lastName(customer.getLastName())
                .phoneNumber(customer.getPhoneNumber())
                .address(null)
                .build();

        when(securityService.isAdmin()).thenReturn(true);
        when(agentRepository.findByAgentCode("JF-008")).thenReturn(Optional.of(agent));
        when(securityService.getCurrentUserId()).thenReturn(adminUserId);
        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));
        when(customerService.findByPhone("0771234567")).thenReturn(customerNoAddress);

        assertThatThrownBy(() -> orderService.createOrder(orderRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no district on file");
    }

    @Test
    void getOrderById_success() {
        UUID orderId = order.getId();
        when(orderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.of(order));
        when(orderMapper.toResponse(order)).thenReturn(new OrderResponse());

        var result = orderService.getOrderById(orderId);

        assertThat(result).isNotNull();
    }

    @Test
    void getOrderById_notFound_throws() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(orderId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Order not found");
    }

    @Test
    void getOrderByOrderNumber_success() {
        when(orderRepository.findByOrderNumberWithDetails("JF-A-20250614-001"))
                .thenReturn(Optional.of(order));
        when(orderMapper.toResponse(order)).thenReturn(new OrderResponse());

        var result = orderService.getOrderByOrderNumber("JF-A-20250614-001");

        assertThat(result).isNotNull();
    }

    @Test
    void getOrdersByAgentId_success() {
        UUID agentId = agent.getId();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));
        when(orderRepository.findByAgentIdWithDetails(agentId)).thenReturn(List.of(order));
        when(orderMapper.toResponseList(anyList())).thenReturn(List.of(new OrderResponse()));

        var results = orderService.getOrdersByAgentId(agentId);

        assertThat(results).hasSize(1);
    }

    @Test
    void getMyOrders_success() {
        when(securityService.getCurrentUserId()).thenReturn(agentUserId);
        when(agentRepository.findByUserId(agentUserId)).thenReturn(Optional.of(agent));
        when(orderRepository.findByAgentIdWithDetails(agent.getId())).thenReturn(List.of(order));
        when(orderMapper.toResponseList(anyList())).thenReturn(List.of(new OrderResponse()));

        var results = orderService.getMyOrders();

        assertThat(results).hasSize(1);
    }

    @Test
    void getAllOrders_success() {
        when(orderRepository.findAllWithDetails()).thenReturn(List.of(order));
        when(orderMapper.toResponseList(anyList())).thenReturn(List.of(new OrderResponse()));

        var results = orderService.getAllOrders();

        assertThat(results).hasSize(1);
    }

    @Test
    void updateOrderStatus_validTransition_succeeds() {
        UUID orderId = order.getId();
        when(orderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderMapper.toResponse(any())).thenReturn(new OrderResponse());

        orderService.updateOrderStatus(orderId, OrderStatus.CONFIRMED);

        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(auditLogService).record(eq(AuditAction.STATUS_CHANGE), eq("Order"), eq(orderId), anyString());
        verify(orderNotificationService).sendOrderStatus(any());
    }

    @Test
    void updateOrderStatus_cancelledOrder_throws() {
        order.setStatus(OrderStatus.CANCELLED);
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateOrderStatus(order.getId(), OrderStatus.CONFIRMED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot update status of a cancelled order");
    }

    @Test
    void updateOrderStatus_deliveredOrder_throws() {
        order.setStatus(OrderStatus.DELIVERED);
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateOrderStatus(order.getId(), OrderStatus.CONFIRMED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot update status of a delivered order");
    }

    @Test
    void updateOrderStatus_returnedOrder_throws() {
        order.setStatus(OrderStatus.RETURNED);
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateOrderStatus(order.getId(), OrderStatus.CONFIRMED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot update status of a returned order");
    }

    @Test
    void updateOrderStatus_terminalStatusViaUpdate_throws() {
        UUID orderId = order.getId();
        when(orderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateOrderStatus(orderId, OrderStatus.DELIVERED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Order status can only be updated up to SHIPPED");
    }

    @Test
    void updateOrderStatus_shipped_setsDeliveryPending() {
        UUID orderId = order.getId();
        when(orderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderMapper.toResponse(any())).thenReturn(new OrderResponse());

        orderService.updateOrderStatus(orderId, OrderStatus.SHIPPED);

        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(orderCaptor.getValue().getDeliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
    }

    @Test
    void cancelOrder_success() {
        UUID orderId = order.getId();
        when(orderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderMapper.toResponse(any())).thenReturn(new OrderResponse());

        orderService.cancelOrder(orderId);

        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(saved.getPaidAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getOutstandingBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        verify(inventoryService).recordOrderStocks(
                anyList(), anyList(), eq(StockMovementType.RETURN), anyList());
        verify(commissionLifecycleService).cancelCommissionForOrder(orderId);
        verify(auditLogService).record(eq(AuditAction.STATUS_CHANGE), eq("Order"), eq(orderId), anyString());
        verify(orderNotificationService).sendOrderCancelled(any());
    }

    @Test
    void cancelOrder_alreadyCancelled_throws() {
        order.setStatus(OrderStatus.CANCELLED);
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(order.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Order is already cancelled");
    }

    @Test
    void cancelOrder_shipped_throws() {
        order.setStatus(OrderStatus.SHIPPED);
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(order.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot cancel a shipped order");
    }

    @Test
    void cancelOrder_delivered_throws() {
        order.setStatus(OrderStatus.DELIVERED);
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(order.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot cancel a delivered order");
    }

    @Test
    void cancelOrder_deliveryFailedPermanent_skipsStockRestore() {
        // If the delivery was already marked FAILED_PERMANENT, DeliveryService
        // already restored the stock. Cancelling the order must not restore it
        // a second time (double-restore = customer can inflate inventory).
        order.setStatus(OrderStatus.PENDING);
        order.setDeliveryStatus(DeliveryStatus.FAILED_PERMANENT);
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderMapper.toResponse(any())).thenReturn(new OrderResponse());

        orderService.cancelOrder(order.getId());

        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        // Stock is NOT restored again -- only commission is cancelled.
        verify(inventoryService, never()).recordOrderStocks(
                anyList(), anyList(), eq(StockMovementType.RETURN), anyList());
        verify(commissionLifecycleService).cancelCommissionForOrder(order.getId());
    }

    @Test
    void cancelOrder_paidCash_setsRefunded() {
        order.setPaymentMethod(PaymentMethod.CASH);
        order.setPaidAmount(new BigDecimal("6500.00"));
        order.setOutstandingBalance(BigDecimal.ZERO);
        order.setPaymentStatus(PaymentStatus.PAID);

        UUID orderId = order.getId();
        when(orderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderMapper.toResponse(any())).thenReturn(new OrderResponse());

        orderService.cancelOrder(orderId);

        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();
        assertThat(saved.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(saved.getPaidAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getOutstandingBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(auditLogService).record(eq(AuditAction.STATUS_CHANGE), eq("Order"), eq(orderId),
                contains("refunded 6500.00"));
    }

    @Test
    void cancelOrder_creditOrder_reversesCustomerBalance() {
        customer.setOutstandingBalance(new BigDecimal("6500.00"));
        order.setPaymentMethod(PaymentMethod.CREDIT);
        order.setPaidAmount(BigDecimal.ZERO);
        order.setOutstandingBalance(new BigDecimal("6500.00"));
        order.setPaymentStatus(PaymentStatus.UNPAID);

        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderMapper.toResponse(any())).thenReturn(new OrderResponse());

        orderService.cancelOrder(order.getId());

        assertThat(customer.getOutstandingBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void cancelOrder_partiallyPaidCredit_reversesCustomerBalanceAndSetsRefunded() {
        customer.setOutstandingBalance(new BigDecimal("4000.00"));
        order.setPaymentMethod(PaymentMethod.CREDIT);
        order.setPaidAmount(new BigDecimal("2500.00"));
        order.setOutstandingBalance(new BigDecimal("4000.00"));
        order.setPaymentStatus(PaymentStatus.PARTIALLY_PAID);

        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderMapper.toResponse(any())).thenReturn(new OrderResponse());

        orderService.cancelOrder(order.getId());

        assertThat(customer.getOutstandingBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();
        assertThat(saved.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(saved.getPaidAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void returnOrder_success() {
        order.setStatus(OrderStatus.DELIVERED);
        order.setPaymentMethod(PaymentMethod.CASH);
        order.setPaidAmount(new BigDecimal("6500.00"));
        order.setOutstandingBalance(BigDecimal.ZERO);
        order.setPaymentStatus(PaymentStatus.PAID);

        UUID orderId = order.getId();
        when(orderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderMapper.toResponse(any())).thenReturn(new OrderResponse());

        orderService.returnOrder(orderId);

        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.RETURNED);
        assertThat(saved.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(saved.getPaidAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getOutstandingBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(inventoryService).recordOrderStocks(
                anyList(), anyList(), eq(StockMovementType.RETURN), anyList());
        verify(commissionLifecycleService).cancelCommissionForOrder(orderId);
        verify(orderNotificationService).sendOrderStatus(any());
    }

    @Test
    void returnOrder_notDelivered_throws() {
        order.setStatus(OrderStatus.SHIPPED);
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.returnOrder(order.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only delivered orders can be returned");
    }

    @Test
    void createOrder_productWithPercentageDiscount_usesDiscountedPriceInTotal() {
        // Admin places order for a product with 20% discount cap.
        // Order total must reflect the discounted price.
        Product discountedProduct = Product.builder()
                .id(UUID.randomUUID())
                .itemCode("DISC-001")
                .name("Discounted Product")
                .price(new BigDecimal("1000.00"))
                .maxDiscountType("PERCENTAGE")
                .maxDiscountValue(new BigDecimal("20"))
                .isActive(true)
                .build();

        OrderItemRequest discountedItem = new OrderItemRequest();
        discountedItem.setProductItemCode("DISC-001");
        discountedItem.setQuantity(3);
        discountedItem.setDiscountType(com.ebee.common.enums.DiscountType.PERCENTAGE);
        discountedItem.setDiscountValue(new BigDecimal("20"));

        OrderRequest discountedRequest = new OrderRequest();
        discountedRequest.setAgentCode("JF-008");
        discountedRequest.setCustomerPhoneNumber("0771234567");
        discountedRequest.setPaymentMethod(PaymentMethod.CASH);
        discountedRequest.setItems(List.of(discountedItem));

        when(securityService.isAdmin()).thenReturn(true);
        when(agentRepository.findByAgentCode("JF-008")).thenReturn(Optional.of(agent));
        when(securityService.getCurrentUserId()).thenReturn(adminUserId);
        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));
        when(customerService.findByPhone("0771234567")).thenReturn(customer);
        when(orderNumberService.generateOrderNumber(District.JF, OrderSource.AGENT))
                .thenReturn("JF-A-20250614-002");
        when(productRepository.findByItemCodeIn(anyList()))
                .thenReturn(List.of(discountedProduct));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(commissionCalculationService.calculateCommissionForOrder(any()))
                .thenReturn(Commission.builder()
                        .id(UUID.randomUUID())
                        .commissionAmount(new BigDecimal("240.00"))
                        .commissionRate(new BigDecimal("7.5"))
                        .status(CommissionStatus.CALCULATED)
                        .build());
        when(orderMapper.toResponse(any())).thenReturn(new OrderResponse());

        orderService.createOrder(discountedRequest);

        // Verify the order was saved with the discounted total:
        // 1000.00 * 0.8 = 800.00, then 800.00 * 3 = 2400.00
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();
        assertThat(savedOrder.getTotalAmount()).isEqualByComparingTo(new BigDecimal("2400.00"));

        // Verify commission was calculated on the discounted total:
        // 2400.00 * 7.5% = 180.00
        ArgumentCaptor<Order> commissionCaptor = ArgumentCaptor.forClass(Order.class);
        verify(commissionCalculationService).calculateCommissionForOrder(commissionCaptor.capture());
        assertThat(commissionCaptor.getValue().getTotalAmount())
                .isEqualByComparingTo(new BigDecimal("2400.00"));
    }

    @Test
    void createOrder_productWithFixedDiscount_usesDiscountedPriceInTotal() {
        // Admin places order for a product with 250 LKR fixed discount cap.
        Product fixedDiscountProduct = Product.builder()
                .id(UUID.randomUUID())
                .itemCode("FIX-001")
                .name("Fixed Discount Product")
                .price(new BigDecimal("1000.00"))
                .maxDiscountType("FIXED")
                .maxDiscountValue(new BigDecimal("250"))
                .isActive(true)
                .build();

        OrderItemRequest item = new OrderItemRequest();
        item.setProductItemCode("FIX-001");
        item.setQuantity(2);
        item.setDiscountType(com.ebee.common.enums.DiscountType.FIXED);
        item.setDiscountValue(new BigDecimal("250"));

        OrderRequest req = new OrderRequest();
        req.setAgentCode("JF-008");
        req.setCustomerPhoneNumber("0771234567");
        req.setPaymentMethod(PaymentMethod.CASH);
        req.setItems(List.of(item));

        when(securityService.isAdmin()).thenReturn(true);
        when(agentRepository.findByAgentCode("JF-008")).thenReturn(Optional.of(agent));
        when(securityService.getCurrentUserId()).thenReturn(adminUserId);
        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));
        when(customerService.findByPhone("0771234567")).thenReturn(customer);
        when(orderNumberService.generateOrderNumber(District.JF, OrderSource.AGENT))
                .thenReturn("JF-A-20250614-003");
        when(productRepository.findByItemCodeIn(anyList()))
                .thenReturn(List.of(fixedDiscountProduct));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(commissionCalculationService.calculateCommissionForOrder(any()))
                .thenReturn(Commission.builder().id(UUID.randomUUID()).build());
        when(orderMapper.toResponse(any())).thenReturn(new OrderResponse());

        orderService.createOrder(req);

        // Verify: 1000.00 - 250.00 = 750.00, then 750.00 * 2 = 1500.00
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getTotalAmount())
                .isEqualByComparingTo(new BigDecimal("1500.00"));
    }

    @Test
    void createOrder_perItemDiscountExceedsProductCap_throws() {
        // Product cap is 10% but admin tries 25% -> should be rejected
        Product cappedProduct = Product.builder()
                .id(UUID.randomUUID())
                .itemCode("CAP-001")
                .name("Capped Product")
                .price(new BigDecimal("1000.00"))
                .maxDiscountType("PERCENTAGE")
                .maxDiscountValue(new BigDecimal("10"))
                .isActive(true)
                .build();

        OrderItemRequest item = new OrderItemRequest();
        item.setProductItemCode("CAP-001");
        item.setQuantity(1);
        item.setDiscountType(com.ebee.common.enums.DiscountType.PERCENTAGE);
        item.setDiscountValue(new BigDecimal("25")); // exceeds 10% cap

        OrderRequest req = new OrderRequest();
        req.setAgentCode("JF-008");
        req.setCustomerPhoneNumber("0771234567");
        req.setPaymentMethod(PaymentMethod.CASH);
        req.setItems(List.of(item));

        when(securityService.isAdmin()).thenReturn(true);
        when(agentRepository.findByAgentCode("JF-008")).thenReturn(Optional.of(agent));
        when(securityService.getCurrentUserId()).thenReturn(adminUserId);
        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));
        when(customerService.findByPhone("0771234567")).thenReturn(customer);
        when(orderNumberService.generateOrderNumber(District.JF, OrderSource.AGENT))
                .thenReturn("JF-A-20250614-004");
        when(productRepository.findByItemCodeIn(anyList()))
                .thenReturn(List.of(cappedProduct));

        assertThatThrownBy(() -> orderService.createOrder(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds the maximum allowed");
    }

    @Test
    void createOrder_perItemDiscountOnProductWithoutCap_throws() {
        // Product has no maxDiscount set -> admin cannot apply any per-order discount
        Product uncappedProduct = Product.builder()
                .id(UUID.randomUUID())
                .itemCode("UNCAP-001")
                .name("Uncapped Product")
                .price(new BigDecimal("1000.00"))
                .isActive(true)
                .build();

        OrderItemRequest item = new OrderItemRequest();
        item.setProductItemCode("UNCAP-001");
        item.setQuantity(1);
        item.setDiscountType(com.ebee.common.enums.DiscountType.PERCENTAGE);
        item.setDiscountValue(new BigDecimal("10"));

        OrderRequest req = new OrderRequest();
        req.setAgentCode("JF-008");
        req.setCustomerPhoneNumber("0771234567");
        req.setPaymentMethod(PaymentMethod.CASH);
        req.setItems(List.of(item));

        when(securityService.isAdmin()).thenReturn(true);
        when(agentRepository.findByAgentCode("JF-008")).thenReturn(Optional.of(agent));
        when(securityService.getCurrentUserId()).thenReturn(adminUserId);
        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));
        when(customerService.findByPhone("0771234567")).thenReturn(customer);
        when(orderNumberService.generateOrderNumber(District.JF, OrderSource.AGENT))
                .thenReturn("JF-A-20250614-005");
        when(productRepository.findByItemCodeIn(anyList()))
                .thenReturn(List.of(uncappedProduct));

        assertThatThrownBy(() -> orderService.createOrder(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not allow per-order discount");
    }

    @Test
    void createOrder_noPerItemDiscount_usesOriginalPrice() {
        // Admin does NOT apply any per-order discount -> original price used
        Product product = Product.builder()
                .id(UUID.randomUUID())
                .itemCode("NODISC-001")
                .name("No Discount Item")
                .price(new BigDecimal("500.00"))
                .maxDiscountType("PERCENTAGE")
                .maxDiscountValue(new BigDecimal("10"))
                .isActive(true)
                .build();

        OrderItemRequest item = new OrderItemRequest();
        item.setProductItemCode("NODISC-001");
        item.setQuantity(2);
        // No discountType / discountValue set

        OrderRequest req = new OrderRequest();
        req.setAgentCode("JF-008");
        req.setCustomerPhoneNumber("0771234567");
        req.setPaymentMethod(PaymentMethod.CASH);
        req.setItems(List.of(item));

        when(securityService.isAdmin()).thenReturn(true);
        when(agentRepository.findByAgentCode("JF-008")).thenReturn(Optional.of(agent));
        when(securityService.getCurrentUserId()).thenReturn(adminUserId);
        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));
        when(customerService.findByPhone("0771234567")).thenReturn(customer);
        when(orderNumberService.generateOrderNumber(District.JF, OrderSource.AGENT))
                .thenReturn("JF-A-20250614-006");
        when(productRepository.findByItemCodeIn(anyList()))
                .thenReturn(List.of(product));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(commissionCalculationService.calculateCommissionForOrder(any()))
                .thenReturn(Commission.builder().id(UUID.randomUUID()).build());
        when(orderMapper.toResponse(any())).thenReturn(new OrderResponse());

        orderService.createOrder(req);

        // No discount -> 500 * 2 = 1000.00
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getTotalAmount())
                .isEqualByComparingTo(new BigDecimal("1000.00"));
    }
}
