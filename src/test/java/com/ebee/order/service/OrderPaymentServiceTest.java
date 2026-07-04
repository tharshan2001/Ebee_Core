package com.ebee.order.service;

import com.ebee.agent.entity.Agent;
import com.ebee.agent.repository.AgentRepository;
import com.ebee.audit.enums.AuditAction;
import com.ebee.audit.service.AuditLogService;
import com.ebee.commission.service.CommissionCalculationService;
import com.ebee.commission.service.CommissionLifecycleService;
import com.ebee.common.enums.District;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.security.SecurityService;
import com.ebee.customer.entity.Address;
import com.ebee.customer.entity.Customer;
import com.ebee.customer.service.CustomerService;
import com.ebee.inventory.service.InventoryService;
import com.ebee.order.dto.OrderItemRequest;
import com.ebee.order.dto.OrderRequest;
import com.ebee.order.dto.OrderResponse;
import com.ebee.order.dto.RecordPaymentRequest;
import com.ebee.order.entity.Order;
import com.ebee.order.entity.OrderItem;
import com.ebee.order.enums.OrderSource;
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
class OrderPaymentServiceTest {

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
    private User adminUser;
    private Agent agent;
    private Customer customer;
    private Product product;
    private OrderRequest baseOrderRequest;

    @BeforeEach
    void setUp() {
        adminUserId = UUID.randomUUID();

        adminUser = User.builder()
                .id(adminUserId)
                .email("admin@ebee.com")
                .firstName("Admin")
                .lastName("User")
                .role(RoleType.ADMIN)
                .build();

        User agentUser = User.builder()
                .id(UUID.randomUUID())
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
                .outstandingBalance(BigDecimal.ZERO)
                .creditLimit(BigDecimal.ZERO)
                .build();

        product = Product.builder()
                .id(UUID.randomUUID())
                .itemCode("PROD-001")
                .name("Product One")
                .price(new BigDecimal("1000.00"))
                .isActive(true)
                .build();

        OrderItemRequest item = new OrderItemRequest();
        item.setProductItemCode("PROD-001");
        item.setQuantity(2);

        baseOrderRequest = new OrderRequest();
        baseOrderRequest.setAgentCode("JF-008");
        baseOrderRequest.setCustomerPhoneNumber("0771234567");
        baseOrderRequest.setItems(List.of(item));
    }

    private void setupCreateMocks() {
        when(securityService.isAdmin()).thenReturn(true);
        when(agentRepository.findByAgentCode("JF-008")).thenReturn(Optional.of(agent));
        when(securityService.getCurrentUserId()).thenReturn(adminUserId);
        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));
        when(customerService.findByPhone("0771234567")).thenReturn(customer);
        when(orderNumberService.generateOrderNumber(District.JF, OrderSource.AGENT))
                .thenReturn("JF-A-20260617-001");
        when(productRepository.findByItemCodeIn(anyList())).thenReturn(List.of(product));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(commissionCalculationService.calculateCommissionForOrder(any()))
                .thenReturn(null);
        when(orderMapper.toResponse(any())).thenReturn(new OrderResponse());
    }

    @Test
    void createOrder_withCash_setsPaymentStatusToPaid() {
        baseOrderRequest.setPaymentMethod(PaymentMethod.CASH);
        setupCreateMocks();

        orderService.createOrder(baseOrderRequest);

        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();

        assertThat(saved.getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(saved.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(saved.getPaidAmount()).isEqualByComparingTo(new BigDecimal("2000.00"));
        assertThat(saved.getOutstandingBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getPaidAt()).isNotNull();
    }

    @Test
    void createOrder_withCardPayment_setsPaymentStatusToPaid() {
        baseOrderRequest.setPaymentMethod(PaymentMethod.CARD_PAYMENT);
        setupCreateMocks();

        orderService.createOrder(baseOrderRequest);

        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();

        assertThat(saved.getPaymentMethod()).isEqualTo(PaymentMethod.CARD_PAYMENT);
        assertThat(saved.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(saved.getPaidAmount()).isEqualByComparingTo(new BigDecimal("2000.00"));
        assertThat(saved.getOutstandingBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void createOrder_withBankTransfer_setsPaymentStatusToPaid() {
        baseOrderRequest.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        setupCreateMocks();

        orderService.createOrder(baseOrderRequest);

        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();

        assertThat(saved.getPaymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(saved.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(saved.getPaidAmount()).isEqualByComparingTo(new BigDecimal("2000.00"));
    }

    @Test
    void createOrder_withCod_setsPaymentStatusToUnpaid() {
        baseOrderRequest.setPaymentMethod(PaymentMethod.COD);
        setupCreateMocks();

        orderService.createOrder(baseOrderRequest);

        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();

        assertThat(saved.getPaymentMethod()).isEqualTo(PaymentMethod.COD);
        assertThat(saved.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(saved.getPaidAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getOutstandingBalance()).isEqualByComparingTo(new BigDecimal("2000.00"));
        assertThat(saved.getPaidAt()).isNull();
    }

    @Test
    void createOrder_withCreditFullPayment_setsPaymentStatusToPaid() {
        baseOrderRequest.setPaymentMethod(PaymentMethod.CREDIT);
        baseOrderRequest.setPaidAmount(new BigDecimal("2000.00"));
        setupCreateMocks();

        orderService.createOrder(baseOrderRequest);

        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();

        assertThat(saved.getPaymentMethod()).isEqualTo(PaymentMethod.CREDIT);
        assertThat(saved.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(saved.getPaidAmount()).isEqualByComparingTo(new BigDecimal("2000.00"));
        assertThat(saved.getOutstandingBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getPaidAt()).isNotNull();
    }

    @Test
    void createOrder_withCreditHalfPaid_setsPaymentStatusToPartiallyPaid() {
        baseOrderRequest.setPaymentMethod(PaymentMethod.CREDIT);
        baseOrderRequest.setPaidAmount(new BigDecimal("1000.00"));
        setupCreateMocks();

        orderService.createOrder(baseOrderRequest);

        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();

        assertThat(saved.getPaymentMethod()).isEqualTo(PaymentMethod.CREDIT);
        assertThat(saved.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID);
        assertThat(saved.getPaidAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(saved.getOutstandingBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(saved.getPaidAt()).isNotNull();
    }

    @Test
    void createOrder_withCreditNoPayment_setsPaymentStatusToUnpaid() {
        baseOrderRequest.setPaymentMethod(PaymentMethod.CREDIT);
        setupCreateMocks();

        orderService.createOrder(baseOrderRequest);

        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();

        assertThat(saved.getPaymentMethod()).isEqualTo(PaymentMethod.CREDIT);
        assertThat(saved.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(saved.getPaidAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getOutstandingBalance()).isEqualByComparingTo(new BigDecimal("2000.00"));
        assertThat(saved.getPaidAt()).isNull();
    }

    @Test
    void recordPayment_onUnpaidOrder_marksAsPaid() {
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .totalAmount(new BigDecimal("2000.00"))
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.UNPAID)
                .paidAmount(BigDecimal.ZERO)
                .outstandingBalance(new BigDecimal("2000.00"))
                .build();

        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderMapper.toResponse(any())).thenReturn(new OrderResponse());

        RecordPaymentRequest request = new RecordPaymentRequest(new BigDecimal("2000.00"), PaymentMethod.CASH);
        orderService.recordPayment(order.getId(), request);

        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();

        assertThat(saved.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(saved.getPaidAmount()).isEqualByComparingTo(new BigDecimal("2000.00"));
        assertThat(saved.getOutstandingBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(saved.getPaidAt()).isNotNull();

        verify(auditLogService).record(eq(AuditAction.UPDATE), eq("Order"), eq(order.getId()), anyString());
    }

    @Test
    void recordPayment_partialPaymentOnUnpaidOrder_setsPartiallyPaid() {
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .totalAmount(new BigDecimal("2000.00"))
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.UNPAID)
                .paidAmount(BigDecimal.ZERO)
                .outstandingBalance(new BigDecimal("2000.00"))
                .build();

        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderMapper.toResponse(any())).thenReturn(new OrderResponse());

        RecordPaymentRequest request = new RecordPaymentRequest(new BigDecimal("800.00"), PaymentMethod.CASH);
        orderService.recordPayment(order.getId(), request);

        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();

        assertThat(saved.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID);
        assertThat(saved.getPaidAmount()).isEqualByComparingTo(new BigDecimal("800.00"));
        assertThat(saved.getOutstandingBalance()).isEqualByComparingTo(new BigDecimal("1200.00"));
    }

    @Test
    void recordPayment_onCodOrder_doesNotUpdateCustomerBalance() {
        customer.setOutstandingBalance(BigDecimal.ZERO);

        Order order = Order.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .totalAmount(new BigDecimal("2000.00"))
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.UNPAID)
                .paidAmount(BigDecimal.ZERO)
                .outstandingBalance(new BigDecimal("2000.00"))
                .build();

        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderMapper.toResponse(any())).thenReturn(new OrderResponse());

        RecordPaymentRequest request = new RecordPaymentRequest(new BigDecimal("800.00"), PaymentMethod.CASH);
        orderService.recordPayment(order.getId(), request);

        assertThat(customer.getOutstandingBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void recordPayment_partialToFullOnPartiallyPaidCreditOrder_marksAsPaidAndReducesCustomerBalance() {
        customer.setOutstandingBalance(new BigDecimal("1200.00"));

        Order order = Order.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .totalAmount(new BigDecimal("2000.00"))
                .paymentMethod(PaymentMethod.CREDIT)
                .paymentStatus(PaymentStatus.PARTIALLY_PAID)
                .paidAmount(new BigDecimal("800.00"))
                .outstandingBalance(new BigDecimal("1200.00"))
                .build();

        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderMapper.toResponse(any())).thenReturn(new OrderResponse());

        RecordPaymentRequest request = new RecordPaymentRequest(new BigDecimal("1200.00"), PaymentMethod.CASH);
        orderService.recordPayment(order.getId(), request);

        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();

        assertThat(saved.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(saved.getPaidAmount()).isEqualByComparingTo(new BigDecimal("2000.00"));
        assertThat(saved.getOutstandingBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(customer.getOutstandingBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void recordPayment_amountExceedsOutstandingBalance_throws() {
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .totalAmount(new BigDecimal("2000.00"))
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.UNPAID)
                .paidAmount(BigDecimal.ZERO)
                .outstandingBalance(new BigDecimal("500.00"))
                .build();

        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));

        RecordPaymentRequest request = new RecordPaymentRequest(new BigDecimal("600.00"), PaymentMethod.CASH);

        assertThatThrownBy(() -> orderService.recordPayment(order.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot exceed outstanding balance");
    }

    @Test
    void recordPayment_onAlreadyPaidOrder_throws() {
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .totalAmount(new BigDecimal("2000.00"))
                .paymentMethod(PaymentMethod.CASH)
                .paymentStatus(PaymentStatus.PAID)
                .paidAmount(new BigDecimal("2000.00"))
                .outstandingBalance(BigDecimal.ZERO)
                .build();

        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));

        RecordPaymentRequest request = new RecordPaymentRequest(new BigDecimal("500.00"), PaymentMethod.CASH);

        assertThatThrownBy(() -> orderService.recordPayment(order.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already fully paid");
    }

    @Test
    void recordPayment_orderNotFound_throws() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.empty());

        RecordPaymentRequest request = new RecordPaymentRequest(new BigDecimal("500.00"), PaymentMethod.CASH);

        assertThatThrownBy(() -> orderService.recordPayment(orderId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Order not found");
    }
}
