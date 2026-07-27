package com.ebee.order.service;

import com.ebee.agent.entity.Agent;
import com.ebee.agent.repository.AgentRepository;
import com.ebee.audit.service.AuditLogService;
import com.ebee.combo.entity.Combo;
import com.ebee.combo.entity.ComboItem;
import com.ebee.combo.entity.ComboRule;
import com.ebee.combo.enums.ComboRuleType;
import com.ebee.combo.repository.ComboRepository;
import com.ebee.commission.service.CommissionCalculationService;
import com.ebee.commission.service.CommissionLifecycleService;
import com.ebee.common.enums.DiscountType;
import com.ebee.common.enums.District;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.security.SecurityService;
import com.ebee.customer.entity.Address;
import com.ebee.customer.entity.Customer;
import com.ebee.customer.repository.CustomerRepository;
import com.ebee.customer.service.CustomerService;
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
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceComboTest {

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
    @Mock private CustomerRepository customerRepository;
    @Mock private CustomerService customerService;
    @Mock private OrderNotificationService orderNotificationService;
    @Mock private AuditLogService auditLogService;
    @Mock private OrderPaymentService orderPaymentService;
    @Mock private OrderPdfService orderPdfService;
    @Mock private ComboRepository comboRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks private OrderServiceImpl orderService;

    @Captor private ArgumentCaptor<Order> orderCaptor;

    private UUID adminUserId;
    private User adminUser;
    private Agent agent;
    private Customer customer;
    private Product product1;
    private Product product2;
    private Product freeProduct;
    private Combo combo;
    private Combo comboWithFree;
    private Combo comboWithDiscount;

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
                .customerCode("CU-123456")
                .build();

        product1 = Product.builder()
                .id(UUID.randomUUID())
                .itemCode("PROD-001")
                .name("Shampoo")
                .price(new BigDecimal("500.00"))
                .isActive(true)
                .build();

        product2 = Product.builder()
                .id(UUID.randomUUID())
                .itemCode("PROD-002")
                .name("Conditioner")
                .price(new BigDecimal("750.00"))
                .isActive(true)
                .build();

        freeProduct = Product.builder()
                .id(UUID.randomUUID())
                .itemCode("FREE-001")
                .name("Free Brush")
                .price(new BigDecimal("300.00"))
                .isActive(true)
                .build();

        combo = Combo.builder()
                .id(UUID.randomUUID())
                .name("Hair Care Bundle")
                .items(new ArrayList<>(List.of(
                        ComboItem.builder().id(UUID.randomUUID()).product(product1).quantity(2).build(),
                        ComboItem.builder().id(UUID.randomUUID()).product(product2).quantity(1).build()
                )))
                .rule(null)
                .build();

        comboWithFree = Combo.builder()
                .id(UUID.randomUUID())
                .name("Bundle + Free Brush")
                .items(new ArrayList<>(List.of(
                        ComboItem.builder().id(UUID.randomUUID()).product(product1).quantity(1).build()
                )))
                .rule(ComboRule.builder()
                        .id(UUID.randomUUID())
                        .ruleType(ComboRuleType.FREE_PRODUCT)
                        .freeProduct(freeProduct)
                        .build())
                .build();

        comboWithDiscount = Combo.builder()
                .id(UUID.randomUUID())
                .name("Discounted Bundle")
                .items(new ArrayList<>(List.of(
                        ComboItem.builder().id(UUID.randomUUID()).product(product1).quantity(2).build(),
                        ComboItem.builder().id(UUID.randomUUID()).product(product2).quantity(1).build()
                )))
                .rule(ComboRule.builder()
                        .id(UUID.randomUUID())
                        .ruleType(ComboRuleType.DISCOUNT)
                        .discountType("FIXED")
                        .discountValue(new BigDecimal("200.00"))
                        .build())
                .build();

        lenient().when(securityService.isAdmin()).thenReturn(true);
        lenient().when(securityService.getCurrentUserId()).thenReturn(adminUserId);
        lenient().when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));
        lenient().when(customerService.findByPhone("0771234567")).thenReturn(customer);
        lenient().when(orderNumberService.generateOrderNumber(any(), any())).thenReturn("JF-A-20250614-001");
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
                }
            } else {
                order.setPaymentStatus(PaymentStatus.PAID);
                order.setPaidAmount(order.getTotalAmount());
                order.setOutstandingBalance(BigDecimal.ZERO);
                order.setPaidAt(LocalDateTime.now());
            }
            return null;
        }).when(orderPaymentService).initPayment(any(), any(), any(), any(), any());
    }

    private void mockOrderSave() {
        lenient().when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            if (order.getId() == null) order.setId(UUID.randomUUID());
            if (order.getOrderNumber() == null) order.setOrderNumber("JF-A-20250614-001");
            return order;
        });
    }

    @Test
    void createOrder_comboWithNullRule_ordersSuccessfully() {
        mockOrderSave();
        when(comboRepository.findByIdWithDetails(combo.getId())).thenReturn(Optional.of(combo));
        when(inventoryService.getAverageCosts(anyList())).thenReturn(Map.of());
        when(orderMapper.toResponse(any())).thenReturn(new OrderResponse());

        OrderRequest request = buildComboRequest(combo.getId());

        orderService.createOrder(request);

        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();
        assertThat(saved.getItems()).hasSize(1);

        OrderItem comboItem = saved.getItems().get(0);
        assertThat(comboItem.getCombo()).isEqualTo(combo);
        assertThat(comboItem.getUnitPrice()).isEqualByComparingTo(new BigDecimal("1750.00"));
        assertThat(comboItem.getIsFreeProduct()).isFalse();
        assertThat(saved.getTotalAmount()).isEqualByComparingTo(new BigDecimal("1750.00"));
    }

    @Test
    void createOrder_comboWithFreeProduct_addsFreeItem() {
        mockOrderSave();
        when(comboRepository.findByIdWithDetails(comboWithFree.getId())).thenReturn(Optional.of(comboWithFree));
        when(inventoryService.getAverageCosts(anyList())).thenReturn(Map.of(
                product1.getId(), new BigDecimal("300.00"),
                freeProduct.getId(), new BigDecimal("100.00")
        ));
        when(orderMapper.toResponse(any())).thenReturn(new OrderResponse());

        OrderRequest request = buildComboRequest(comboWithFree.getId());

        orderService.createOrder(request);

        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();
        assertThat(saved.getItems()).hasSize(2);

        OrderItem freeItem = saved.getItems().stream()
                .filter(i -> Boolean.TRUE.equals(i.getIsFreeProduct()))
                .findFirst().orElseThrow();
        assertThat(freeItem.getProduct()).isEqualTo(freeProduct);
        assertThat(freeItem.getUnitPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(freeItem.getCostPrice()).isEqualByComparingTo(new BigDecimal("100.00"));

        OrderItem comboItem = saved.getItems().stream()
                .filter(i -> i.getCombo() != null)
                .findFirst().orElseThrow();
        assertThat(comboItem.getUnitPrice()).isEqualByComparingTo(new BigDecimal("500.00"));

        assertThat(saved.getTotalAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    void createOrder_comboWithFixedDiscount_appliesDiscount() {
        mockOrderSave();
        when(comboRepository.findByIdWithDetails(comboWithDiscount.getId())).thenReturn(Optional.of(comboWithDiscount));
        when(inventoryService.getAverageCosts(anyList())).thenReturn(Map.of());
        when(orderMapper.toResponse(any())).thenReturn(new OrderResponse());

        OrderRequest request = buildComboRequest(comboWithDiscount.getId());

        orderService.createOrder(request);

        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();
        assertThat(saved.getItems()).hasSize(1);

        OrderItem comboItem = saved.getItems().get(0);
        assertThat(comboItem.getUnitPrice()).isEqualByComparingTo(new BigDecimal("1550.00"));
        assertThat(comboItem.getOriginalUnitPrice()).isEqualByComparingTo(new BigDecimal("1750.00"));
        assertThat(saved.getTotalAmount()).isEqualByComparingTo(new BigDecimal("1550.00"));
    }

    @Test
    void createOrder_inactiveCombo_throws() {
        Combo inactiveCombo = Combo.builder()
                .id(UUID.randomUUID()).name("Dead").isActive(false).items(List.of()).build();
        when(comboRepository.findByIdWithDetails(inactiveCombo.getId())).thenReturn(Optional.of(inactiveCombo));

        OrderRequest request = buildComboRequest(inactiveCombo.getId());

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void createOrder_comboWithInactiveProduct_throws() {
        Product inactive = Product.builder()
                .id(UUID.randomUUID()).name("Dead").isActive(false).build();
        Combo badCombo = Combo.builder()
                .id(UUID.randomUUID()).name("Bad").items(new ArrayList<>(List.of(
                        ComboItem.builder().id(UUID.randomUUID()).product(inactive).quantity(1).build()
                ))).build();
        when(comboRepository.findByIdWithDetails(badCombo.getId())).thenReturn(Optional.of(badCombo));

        OrderRequest request = buildComboRequest(badCombo.getId());

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inactive product");
    }

    @Test
    void createOrder_comboWithInactiveFreeProduct_throws() {
        Product inactiveFree = Product.builder()
                .id(UUID.randomUUID()).name("Dead Free").isActive(false).build();
        Combo badFreeCombo = Combo.builder()
                .id(UUID.randomUUID()).name("Bad Free").items(new ArrayList<>(List.of(
                        ComboItem.builder().id(UUID.randomUUID()).product(product1).quantity(1).build()
                ))).rule(ComboRule.builder()
                        .id(UUID.randomUUID())
                        .ruleType(ComboRuleType.FREE_PRODUCT)
                        .freeProduct(inactiveFree)
                        .build())
                .build();
        when(comboRepository.findByIdWithDetails(badFreeCombo.getId())).thenReturn(Optional.of(badFreeCombo));

        OrderRequest request = buildComboRequest(badFreeCombo.getId());

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("free product");
    }

    @Test
    void createOrder_comboStockUnavailable_throws() {
        when(comboRepository.findByIdWithDetails(combo.getId())).thenReturn(Optional.of(combo));
        doThrow(new IllegalArgumentException("Insufficient stock for Shampoo"))
                .when(inventoryService).checkStockAvailability(anyList(), anyList());

        OrderRequest request = buildComboRequest(combo.getId());

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    void createOrder_comboAndRegularItems_ordersBoth() {
        mockOrderSave();
        when(comboRepository.findByIdWithDetails(combo.getId())).thenReturn(Optional.of(combo));
        when(inventoryService.getAverageCosts(anyList())).thenReturn(Map.of());
        when(orderMapper.toResponse(any())).thenReturn(new OrderResponse());

        when(productRepository.findByItemCodeIn(anyList())).thenReturn(List.of(product1));

        OrderRequest request = new OrderRequest();
        request.setCustomerPhoneNumber("0771234567");
        request.setPaymentMethod(PaymentMethod.CASH);

        OrderItemRequest regularItem = new OrderItemRequest();
        regularItem.setProductItemCode("PROD-001");
        regularItem.setQuantity(1);

        OrderItemRequest comboItemReq = new OrderItemRequest();
        comboItemReq.setComboId(combo.getId());
        comboItemReq.setQuantity(1);

        request.setItems(List.of(regularItem, comboItemReq));

        orderService.createOrder(request);

        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();
        assertThat(saved.getItems()).hasSize(2);
        assertThat(saved.getTotalAmount()).isEqualByComparingTo(new BigDecimal("2250.00"));
    }

    private OrderRequest buildComboRequest(UUID comboId) {
        OrderRequest request = new OrderRequest();
        request.setCustomerPhoneNumber("0771234567");
        request.setPaymentMethod(PaymentMethod.CASH);

        OrderItemRequest item = new OrderItemRequest();
        item.setComboId(comboId);
        item.setQuantity(1);
        request.setItems(List.of(item));

        return request;
    }
}
