package com.ebee.order.service;

import com.ebee.agent.entity.Agent;
import com.ebee.agent.repository.AgentRepository;
import com.ebee.audit.service.AuditLogService;
import com.ebee.commission.entity.Commission;
import com.ebee.commission.enums.CommissionStatus;
import com.ebee.commission.service.CommissionCalculationService;
import com.ebee.commission.service.CommissionLifecycleService;
import com.ebee.common.enums.DiscountType;
import com.ebee.common.enums.District;
import com.ebee.common.security.SecurityService;
import com.ebee.customer.entity.Address;
import com.ebee.customer.entity.Customer;
import com.ebee.customer.service.CustomerService;
import com.ebee.inventory.service.InventoryService;
import com.ebee.order.dto.OrderItemRequest;
import com.ebee.order.dto.OrderRequest;
import com.ebee.order.dto.OrderResponse;
import com.ebee.order.entity.Order;
import com.ebee.order.entity.OrderItem;
import com.ebee.order.enums.OrderSource;
import com.ebee.order.enums.PaymentMethod;
import com.ebee.order.enums.PaymentStatus;
import com.ebee.order.mapper.OrderMapper;
import com.ebee.order.repository.OrderRepository;
import com.ebee.order.repository.PaymentRepository;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderDiscountIntegrationTest {

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
    @Mock private OrderPaymentService orderPaymentService;
    @Mock private OrderPdfService orderPdfService;
    @Mock private PaymentRepository paymentRepository;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks private OrderServiceImpl orderService;

    @Captor private ArgumentCaptor<Order> orderCaptor;
    @Captor private ArgumentCaptor<Commission> commissionCaptor;
    @Captor private ArgumentCaptor<Order> commissionOrderCaptor;

    private UUID adminUserId;
    private User adminUser;
    private Agent agent;
    private Customer customer;
    private Product product;

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
                .commissionRate(BigDecimal.valueOf(10.0))
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
                .customerCode("CU-123456")
                .build();
    }

    private void setupMocks(OrderRequest request, Commission commission) {
        when(securityService.isAdmin()).thenReturn(true);
        when(agentRepository.findByAgentCode(request.getAgentCode())).thenReturn(Optional.of(agent));
        when(securityService.getCurrentUserId()).thenReturn(adminUserId);
        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));
        when(customerService.findByPhone(request.getCustomerPhoneNumber())).thenReturn(customer);
        when(orderNumberService.generateOrderNumber(District.JF, OrderSource.AGENT))
                .thenReturn("JF-A-20260617-001");
        when(productRepository.findByItemCodeIn(anyList())).thenReturn(List.of(product));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(commissionCalculationService.calculateCommissionForOrder(any())).thenReturn(commission);
        when(orderMapper.toResponse(any())).thenReturn(new OrderResponse());
        configureOrderPaymentMock();
    }

    private void configureOrderPaymentMock() {
        lenient().doAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            PaymentMethod paymentMethod = invocation.getArgument(1);
            BigDecimal paidAmount = invocation.getArgument(2);
            if (paymentMethod == PaymentMethod.COD) {
                order.setPaymentMethod(PaymentMethod.COD);
                order.setPaymentStatus(PaymentStatus.UNPAID);
                order.setPaidAmount(BigDecimal.ZERO);
                order.setOutstandingBalance(order.getTotalAmount());
            } else if (paymentMethod == PaymentMethod.CREDIT) {
                order.setPaymentMethod(PaymentMethod.CREDIT);
                BigDecimal paid = paidAmount != null ? paidAmount : BigDecimal.ZERO;
                order.setPaidAmount(paid);
                if (paid.compareTo(BigDecimal.ZERO) <= 0) {
                    order.setPaymentStatus(PaymentStatus.UNPAID);
                    order.setOutstandingBalance(order.getTotalAmount());
                } else if (paid.compareTo(order.getTotalAmount()) >= 0) {
                    order.setPaymentStatus(PaymentStatus.PAID);
                    order.setPaidAmount(order.getTotalAmount());
                    order.setOutstandingBalance(BigDecimal.ZERO);
                    order.setPaidAt(LocalDateTime.now());
                } else {
                    order.setPaymentStatus(PaymentStatus.PARTIALLY_PAID);
                    order.setOutstandingBalance(order.getTotalAmount().subtract(paid));
                    order.setPaidAt(LocalDateTime.now());
                }
            } else {
                order.setPaymentMethod(paymentMethod);
                order.setPaymentStatus(PaymentStatus.PAID);
                order.setPaidAmount(order.getTotalAmount());
                order.setOutstandingBalance(BigDecimal.ZERO);
                order.setPaidAt(LocalDateTime.now());
            }
            return null;
        }).when(orderPaymentService).initPayment(any(), any(), any(), any(), any());
    }

    @Test
    void createOrder_withPercentageDiscountAndFixedCap_persistsCorrectPrices() {
        product = Product.builder()
                .id(UUID.randomUUID())
                .itemCode("E2E-001")
                .name("E2E Product")
                .price(new BigDecimal("1000.00"))
                .maxDiscountType("FIXED")
                .maxDiscountValue(new BigDecimal("250"))
                .isActive(true)
                .build();

        OrderItemRequest item = new OrderItemRequest();
        item.setProductItemCode("E2E-001");
        item.setQuantity(3);
        item.setDiscountType(DiscountType.FIXED);
        item.setDiscountValue(new BigDecimal("150"));

        OrderRequest request = new OrderRequest();
        request.setAgentCode("JF-008");
        request.setCustomerPhoneNumber("0771234567");
        request.setPaymentMethod(PaymentMethod.CASH);
        request.setItems(List.of(item));

        Commission commission = Commission.builder()
                .id(UUID.randomUUID())
                .commissionAmount(new BigDecimal("255.00"))
                .commissionRate(new BigDecimal("10"))
                .status(CommissionStatus.CALCULATED)
                .build();

        setupMocks(request, commission);

        orderService.createOrder(request);

        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();

        OrderItem savedItem = saved.getItems().getFirst();
        assertThat(savedItem.getUnitPrice()).isEqualByComparingTo("850.00");
        assertThat(savedItem.getOriginalUnitPrice()).isEqualByComparingTo("1000.00");
        assertThat(savedItem.getQuantity()).isEqualTo(3);
        assertThat(savedItem.getSubtotal()).isEqualByComparingTo("2550.00");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("2550.00");

        verify(commissionCalculationService).calculateCommissionForOrder(commissionOrderCaptor.capture());
        assertThat(commissionOrderCaptor.getValue().getTotalAmount()).isEqualByComparingTo("2550.00");
    }

    @Test
    void createOrder_withProductOwnDiscountAndPerOrderDiscount_stacksCorrectly() {
        product = Product.builder()
                .id(UUID.randomUUID())
                .itemCode("STACK-001")
                .name("Stacked Product")
                .price(new BigDecimal("2000.00"))
                .discountType("PERCENTAGE")
                .discountValue(new BigDecimal("10"))
                .maxDiscountType("PERCENTAGE")
                .maxDiscountValue(new BigDecimal("20"))
                .isActive(true)
                .build();

        // Product own discount: 10% -> effective price = 1800.00
        // Per-order discount: 20% on effective -> 1800 * 0.8 = 1440.00
        // Total for qty 2: 1440 * 2 = 2880.00

        OrderItemRequest item = new OrderItemRequest();
        item.setProductItemCode("STACK-001");
        item.setQuantity(2);
        item.setDiscountType(DiscountType.PERCENTAGE);
        item.setDiscountValue(new BigDecimal("20"));

        OrderRequest request = new OrderRequest();
        request.setAgentCode("JF-008");
        request.setCustomerPhoneNumber("0771234567");
        request.setPaymentMethod(PaymentMethod.CASH);
        request.setItems(List.of(item));

        Commission commission = Commission.builder()
                .id(UUID.randomUUID())
                .commissionAmount(new BigDecimal("288.00"))
                .commissionRate(new BigDecimal("10"))
                .status(CommissionStatus.CALCULATED)
                .build();

        setupMocks(request, commission);

        orderService.createOrder(request);

        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();
        OrderItem savedItem = saved.getItems().getFirst();

        assertThat(savedItem.getUnitPrice()).isEqualByComparingTo("1440.00");
        assertThat(savedItem.getOriginalUnitPrice()).isEqualByComparingTo("2000.00");
        assertThat(savedItem.getSubtotal()).isEqualByComparingTo("2880.00");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("2880.00");

        verify(commissionCalculationService).calculateCommissionForOrder(commissionOrderCaptor.capture());
        assertThat(commissionOrderCaptor.getValue().getTotalAmount()).isEqualByComparingTo("2880.00");
    }

    @Test
    void createOrder_withoutPerItemDiscount_usesOriginalPrice() {
        product = Product.builder()
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

        OrderRequest request = new OrderRequest();
        request.setAgentCode("JF-008");
        request.setCustomerPhoneNumber("0771234567");
        request.setPaymentMethod(PaymentMethod.CASH);
        request.setItems(List.of(item));

        Commission commission = Commission.builder()
                .id(UUID.randomUUID())
                .commissionAmount(new BigDecimal("100.00"))
                .build();

        setupMocks(request, commission);

        orderService.createOrder(request);

        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();
        OrderItem savedItem = saved.getItems().getFirst();

        assertThat(savedItem.getUnitPrice()).isEqualByComparingTo("500.00");
        assertThat(savedItem.getOriginalUnitPrice()).isEqualByComparingTo("500.00");
        assertThat(savedItem.getSubtotal()).isEqualByComparingTo("1000.00");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void createOrder_notifiesWithCorrectOrderAndCommission() {
        product = Product.builder()
                .id(UUID.randomUUID())
                .itemCode("NOTIFY-001")
                .name("Notify Test Product")
                .price(new BigDecimal("1000.00"))
                .maxDiscountType("PERCENTAGE")
                .maxDiscountValue(new BigDecimal("15"))
                .isActive(true)
                .build();

        OrderItemRequest item = new OrderItemRequest();
        item.setProductItemCode("NOTIFY-001");
        item.setQuantity(2);
        item.setDiscountType(DiscountType.PERCENTAGE);
        item.setDiscountValue(new BigDecimal("10"));

        OrderRequest request = new OrderRequest();
        request.setAgentCode("JF-008");
        request.setCustomerPhoneNumber("0771234567");
        request.setNotes("Test notification order");
        request.setItems(List.of(item));

        Commission commission = Commission.builder()
                .id(UUID.randomUUID())
                .commissionAmount(new BigDecimal("180.00"))
                .commissionRate(new BigDecimal("10"))
                .status(CommissionStatus.CALCULATED)
                .build();

        setupMocks(request, commission);

        orderService.createOrder(request);

        verify(orderNotificationService).sendOrderCreated(orderCaptor.capture(), commissionCaptor.capture());

        Order notifiedOrder = orderCaptor.getValue();
        Commission notifiedCommission = commissionCaptor.getValue();

        assertThat(notifiedOrder.getTotalAmount()).isEqualByComparingTo("1800.00");
        assertThat(notifiedOrder.getNotes()).isEqualTo("Test notification order");
        assertThat(notifiedCommission.getCommissionAmount()).isEqualByComparingTo("180.00");

        verify(inventoryService).recordOrderStocks(
                anyList(), anyList(), eq(StockMovementType.OUT), anyList());
        verify(customerService).incrementTotalOrders(customer.getId());
        verify(auditLogService).record(any(), any(), any(), anyString());
    }

    @Test
    void createOrder_withMultipleItems_appliesDiscountPerItem() {
        Product product2 = Product.builder()
                .id(UUID.randomUUID())
                .itemCode("MULTI-002")
                .name("Second Product")
                .price(new BigDecimal("500.00"))
                .maxDiscountType("FIXED")
                .maxDiscountValue(new BigDecimal("100"))
                .isActive(true)
                .build();

        product = Product.builder()
                .id(UUID.randomUUID())
                .itemCode("MULTI-001")
                .name("First Product")
                .price(new BigDecimal("1000.00"))
                .maxDiscountType("PERCENTAGE")
                .maxDiscountValue(new BigDecimal("10"))
                .isActive(true)
                .build();

        OrderItemRequest item1 = new OrderItemRequest();
        item1.setProductItemCode("MULTI-001");
        item1.setQuantity(1);
        item1.setDiscountType(DiscountType.PERCENTAGE);
        item1.setDiscountValue(new BigDecimal("10"));

        OrderItemRequest item2 = new OrderItemRequest();
        item2.setProductItemCode("MULTI-002");
        item2.setQuantity(4);
        item2.setDiscountType(DiscountType.FIXED);
        item2.setDiscountValue(new BigDecimal("50"));

        OrderRequest request = new OrderRequest();
        request.setAgentCode("JF-008");
        request.setCustomerPhoneNumber("0771234567");
        request.setPaymentMethod(PaymentMethod.CASH);
        request.setItems(List.of(item1, item2));

        Commission commission = Commission.builder()
                .id(UUID.randomUUID())
                .commissionAmount(new BigDecimal("270.00"))
                .build();

        when(securityService.isAdmin()).thenReturn(true);
        when(agentRepository.findByAgentCode("JF-008")).thenReturn(Optional.of(agent));
        when(securityService.getCurrentUserId()).thenReturn(adminUserId);
        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));
        when(customerService.findByPhone("0771234567")).thenReturn(customer);
        when(orderNumberService.generateOrderNumber(District.JF, OrderSource.AGENT))
                .thenReturn("JF-A-20260617-002");
        when(productRepository.findByItemCodeIn(anyList())).thenReturn(List.of(product, product2));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(commissionCalculationService.calculateCommissionForOrder(any())).thenReturn(commission);
        when(orderMapper.toResponse(any())).thenReturn(new OrderResponse());

        orderService.createOrder(request);

        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();

        assertThat(saved.getItems()).hasSize(2);

        OrderItem firstItem = saved.getItems().get(0);
        assertThat(firstItem.getProduct().getItemCode()).isEqualTo("MULTI-001");
        assertThat(firstItem.getUnitPrice()).isEqualByComparingTo("900.00");
        assertThat(firstItem.getOriginalUnitPrice()).isEqualByComparingTo("1000.00");
        assertThat(firstItem.getQuantity()).isEqualTo(1);
        assertThat(firstItem.getSubtotal()).isEqualByComparingTo("900.00");

        OrderItem secondItem = saved.getItems().get(1);
        assertThat(secondItem.getProduct().getItemCode()).isEqualTo("MULTI-002");
        assertThat(secondItem.getUnitPrice()).isEqualByComparingTo("450.00");
        assertThat(secondItem.getOriginalUnitPrice()).isEqualByComparingTo("500.00");
        assertThat(secondItem.getQuantity()).isEqualTo(4);
        assertThat(secondItem.getSubtotal()).isEqualByComparingTo("1800.00");

        assertThat(saved.getTotalAmount()).isEqualByComparingTo("2700.00");
    }
}
