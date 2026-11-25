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
import com.ebee.customer.repository.CustomerRepository;
import com.ebee.customer.service.CustomerService;
import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.inventory.service.InventoryService;
import com.ebee.order.dto.OrderItemRequest;
import com.ebee.order.dto.OrderRequest;
import com.ebee.order.dto.OrderResponse;
import com.ebee.order.dto.OrderSettlementRequest;
import com.ebee.order.dto.ReturnItemRequest;
import com.ebee.order.dto.ReturnOrderItemRequest;
import com.ebee.order.entity.Order;
import com.ebee.order.entity.OrderItem;
import com.ebee.order.enums.OrderSource;
import com.ebee.order.enums.OrderStatus;
import com.ebee.order.enums.PaymentMethod;
import com.ebee.order.enums.PaymentStatus;
import com.ebee.order.enums.RefundChoice;
import com.ebee.customer.enums.DuesChangeType;
import com.ebee.order.mapper.OrderMapper;
import com.ebee.order.repository.OrderRepository;
import com.ebee.order.repository.PaymentRepository;
import com.ebee.product.entity.Product;
import com.ebee.product.repository.ProductRepository;
import com.ebee.stock.enums.StockMovementType;
import com.ebee.trip.entity.Trip;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final java.util.UUID MAIN_LOCATION_ID = java.util.UUID.randomUUID();

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
    @Mock private PaymentRepository paymentRepository;
    @Mock private com.ebee.accounting.service.LedgerService ledgerService;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @org.mockito.Mock private com.ebee.tax.service.TaxService taxService;
    @org.mockito.Mock private com.ebee.tax.service.VatService vatService;
    @org.mockito.Mock private com.ebee.finance.service.MoneyAccountService moneyAccountService;
    @org.mockito.Mock private com.ebee.location.service.LocationService locationService;
    @org.mockito.Mock private com.ebee.order.repository.OrderItemLotRepository orderItemLotRepository;
    @org.mockito.Mock private com.ebee.pricing.service.PricingService pricingService;
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
        // Normal prices, sold by the stock unit (no price groups or packs configured).
        lenient().when(pricingService.quote(any(), any(), any())).thenAnswer(inv -> {
            com.ebee.product.entity.Product p = inv.getArgument(0);
            return new com.ebee.pricing.service.PricingService.Quote(p.getPrice(), 1, null, false);
        });
        // Single default location, like a fresh V127 database.
        com.ebee.location.entity.Location mainLocation = new com.ebee.location.entity.Location("MAIN", "Main",
                com.ebee.location.entity.Location.Type.WAREHOUSE);
        org.springframework.test.util.ReflectionTestUtils.setField(mainLocation, "id", MAIN_LOCATION_ID);
        lenient().when(locationService.defaultLocation()).thenReturn(mainLocation);
        lenient().when(locationService.resolve(any(), any())).thenReturn(mainLocation);
        lenient().when(locationService.require(any())).thenReturn(mainLocation);
        lenient().when(taxService.getSettings()).thenReturn(new com.ebee.tax.entity.BusinessSettings());
        // FIFO deduction returns one cost per stock row; default each row to 100.00 per unit.
        lenient().when(inventoryService.deductStockWithLots(any(), anyList(), anyList(), anyList())).thenAnswer(inv -> {
            java.util.List<java.util.UUID> ids = inv.getArgument(1);
            java.util.List<Integer> qtys = inv.getArgument(2);
            java.util.List<java.util.List<com.ebee.inventory.dto.LotAllocation>> rows = new java.util.ArrayList<>();
            for (int r = 0; r < qtys.size(); r++) {
                rows.add(java.util.List.of(new com.ebee.inventory.dto.LotAllocation(ids.get(r), null, qtys.get(r),
                        new java.math.BigDecimal("100.00"))));
            }
            return rows;
        });
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
                .outstandingBalance(BigDecimal.ZERO)
                .creditLimit(BigDecimal.ZERO)
                .customerCode("CU-123456")
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
                                .id(UUID.randomUUID())
                                .product(product1)
                                .quantity(2)
                                .unitPrice(new BigDecimal("2500.00"))
                                .subtotal(new BigDecimal("5000.00"))
                                .build(),
                        OrderItem.builder()
                                .id(UUID.randomUUID())
                                .product(product2)
                                .quantity(1)
                                .unitPrice(new BigDecimal("1500.00"))
                                .subtotal(new BigDecimal("1500.00"))
                                .build()
                ))
                .totalAmount(new BigDecimal("6500.00"))
                .build();

        configureOrderPaymentServiceMock();
    }

    private void configureOrderPaymentServiceMock() {
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

        lenient().doAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            if (customer != null) {
                customer.setOutstandingBalance(BigDecimal.ZERO);
            }
            return null;
        }).when(orderPaymentService).syncCustomerOutstanding(any());

        // Real settlement logic on top of the mocks, so cancel/return tests exercise the actual rules.
        org.springframework.test.util.ReflectionTestUtils.setField(orderService, "orderSettlementService",
                new OrderSettlementService(inventoryService, ledgerService, customerService, securityService, org.mockito.Mockito.mock(com.ebee.tax.service.VatService.class), org.mockito.Mockito.mock(com.ebee.finance.service.MoneyAccountService.class), org.mockito.Mockito.mock(com.ebee.order.repository.OrderItemLotRepository.class)));
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
        // Stock comes from the resolved location (here the default "Main").
        verify(inventoryService).deductStockWithLots(eq(MAIN_LOCATION_ID), anyList(), anyList(), anyList());
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
                .customerCode("CU-789012")
                .build();

        when(securityService.isAdmin()).thenReturn(true);
        when(agentRepository.findByAgentCode("JF-008")).thenReturn(Optional.of(agent));
        when(securityService.getCurrentUserId()).thenReturn(adminUserId);
        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));
        when(customerService.findByPhone("0771234567")).thenReturn(customerNoAddress);

        assertThatThrownBy(() -> orderService.createOrder(orderRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Customer has no district");
    }

    // --- Payment-method-specific createOrder tests ---

    private Product paymentProduct;
    private OrderRequest singleItemRequest;

    private void setupSingleItemCreateMocks(OrderRequest request) {
        paymentProduct = Product.builder()
                .id(UUID.randomUUID())
                .itemCode("PROD-PAY")
                .name("Payment Product")
                .price(new BigDecimal("1000.00"))
                .isActive(true)
                .build();

        when(securityService.isAdmin()).thenReturn(true);
        when(agentRepository.findByAgentCode("JF-008")).thenReturn(Optional.of(agent));
        when(securityService.getCurrentUserId()).thenReturn(adminUserId);
        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));
        when(customerService.findByPhone("0771234567")).thenReturn(customer);
        when(orderNumberService.generateOrderNumber(District.JF, OrderSource.AGENT))
                .thenReturn("JF-A-20260617-001");
        when(productRepository.findByItemCodeIn(anyList())).thenReturn(List.of(paymentProduct));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(commissionCalculationService.calculateCommissionForOrder(any()))
                .thenReturn(null);
        when(orderMapper.toResponse(any())).thenReturn(new OrderResponse());
    }

    private OrderRequest createSingleItemRequest(PaymentMethod paymentMethod) {
        OrderItemRequest item = new OrderItemRequest();
        item.setProductItemCode("PROD-PAY");
        item.setQuantity(2);
        OrderRequest request = new OrderRequest();
        request.setAgentCode("JF-008");
        request.setCustomerPhoneNumber("0771234567");
        request.setPaymentMethod(paymentMethod);
        request.setItems(List.of(item));
        return request;
    }

    @Test
    void createOrder_withCash_setsPaymentStatusToPaid() {
        OrderRequest request = createSingleItemRequest(PaymentMethod.CASH);
        setupSingleItemCreateMocks(request);
        orderService.createOrder(request);
        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();
        assertThat(saved.getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(saved.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(saved.getPaidAmount()).isEqualByComparingTo(new BigDecimal("2000.00"));
        assertThat(saved.getOutstandingBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getPaidAt()).isNotNull();
    }

    private UUID vatOn(boolean pricesIncludeTax) {
        com.ebee.tax.entity.BusinessSettings s = new com.ebee.tax.entity.BusinessSettings();
        s.setVatRegistered(true);
        s.setVatRegistrationNumber("123456789-7000");
        s.setPricesIncludeTax(pricesIncludeTax);
        when(taxService.getSettings()).thenReturn(s);
        UUID rateId = UUID.randomUUID();
        when(taxService.effectiveRates(anyCollection())).thenReturn(java.util.Map.of(rateId, new BigDecimal("18.00")));
        return rateId;
    }

    @Test
    void createOrder_vatInclusive_splitsVatOutOfThePrice_totalUnchanged() {
        UUID rateId = vatOn(true);
        OrderRequest request = createSingleItemRequest(PaymentMethod.COD);
        setupSingleItemCreateMocks(request);
        paymentProduct.setTaxRateId(rateId);

        orderService.createOrder(request);

        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();
        OrderItem line = saved.getItems().get(0);
        // 1,000 incl. 18% → 152.54 VAT per unit; 2 units.
        assertThat(line.getUnitPrice()).isEqualByComparingTo("1000.00");
        assertThat(line.getUnitTax()).isEqualByComparingTo("152.54");
        assertThat(line.getTaxAmount()).isEqualByComparingTo("305.08");
        assertThat(line.getTaxRate()).isEqualByComparingTo("18.00");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("2000.00");
        assertThat(saved.getTaxTotal()).isEqualByComparingTo("305.08");
        verify(vatService).post(eq(com.ebee.tax.entity.VatEntry.Direction.OUTPUT),
                eq(com.ebee.tax.entity.VatEntry.SourceType.ORDER), any(), anyString(),
                argThat(a -> a.compareTo(new BigDecimal("1694.92")) == 0),
                argThat(a -> a.compareTo(new BigDecimal("305.08")) == 0));
    }

    @Test
    void createOrder_vatExclusive_addsVatOnTop() {
        UUID rateId = vatOn(false);
        OrderRequest request = createSingleItemRequest(PaymentMethod.COD);
        setupSingleItemCreateMocks(request);
        paymentProduct.setTaxRateId(rateId);

        orderService.createOrder(request);

        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();
        assertThat(saved.getItems().get(0).getUnitPrice()).isEqualByComparingTo("1180.00");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("2360.00");
        assertThat(saved.getTaxTotal()).isEqualByComparingTo("360.00");
        // The customer owes the gross amount.
        verify(ledgerService).recordCustomerInvoice(any(), any(),
                argThat(a -> a.compareTo(new BigDecimal("2360.00")) == 0), anyString(), any());
    }

    @Test
    void createOrder_vatOff_ignoresProductTaxRate() {
        OrderRequest request = createSingleItemRequest(PaymentMethod.COD);
        setupSingleItemCreateMocks(request);
        paymentProduct.setTaxRateId(UUID.randomUUID());

        orderService.createOrder(request);

        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getTaxTotal()).isZero();
        assertThat(orderCaptor.getValue().getTotalAmount()).isEqualByComparingTo("2000.00");
        verify(taxService, never()).effectiveRates(anyCollection());
    }

    @Test
    void createOrder_byThePack_pricesPerPack_andMovesStockInUnits() {
        OrderRequest request = createSingleItemRequest(PaymentMethod.COD); // quantity 2
        setupSingleItemCreateMocks(request);
        UUID boxId = UUID.randomUUID();
        request.getItems().get(0).setUnitId(boxId);
        var box = new com.ebee.pricing.entity.ProductUnit(paymentProduct.getId(), new com.ebee.pricing.entity.Unit("Box"), 12,
                new BigDecimal("11000.00"));
        org.springframework.test.util.ReflectionTestUtils.setField(box, "id", boxId);
        org.mockito.Mockito.doReturn(new com.ebee.pricing.service.PricingService.Quote(new BigDecimal("11000.00"), 12, box, false))
                .when(pricingService).quote(any(), any(), eq(boxId));

        orderService.createOrder(request);

        verify(orderRepository).save(orderCaptor.capture());
        OrderItem line = orderCaptor.getValue().getItems().get(0);
        assertThat(line.getQuantity()).isEqualTo(2);                 // 2 boxes
        assertThat(line.getUnitPrice()).isEqualByComparingTo("11000.00");
        assertThat(line.getSalesUnitName()).isEqualTo("Box of 12");
        assertThat(orderCaptor.getValue().getTotalAmount()).isEqualByComparingTo("22000.00");
        verify(inventoryService).deductStockWithLots(any(), anyList(), eq(List.of(24)), anyList()); // 24 units
    }

    @Test
    void createOrder_withUpfrontPayment_postsPaymentToLedger() {
        OrderRequest request = createSingleItemRequest(PaymentMethod.CREDIT);
        request.setPaidAmount(new BigDecimal("2000.00"));
        setupSingleItemCreateMocks(request);

        orderService.createOrder(request);

        // Invoice and upfront payment both reach the receivable ledger, so the customer owes 0.
        verify(ledgerService).recordCustomerInvoice(any(), any(),
                argThat(a -> a.compareTo(new BigDecimal("2000.00")) == 0), anyString(), any());
        verify(ledgerService).recordCustomerPayment(any(), any(),
                argThat(a -> a.compareTo(new BigDecimal("2000.00")) == 0), contains("Upfront"), any());
    }

    @Test
    void createOrder_codOrder_postsNoPayment() {
        OrderRequest request = createSingleItemRequest(PaymentMethod.COD);
        setupSingleItemCreateMocks(request);

        orderService.createOrder(request);

        verify(ledgerService, never()).recordCustomerPayment(any(), any(), any(), any(), any());
    }

    @Test
    void createOrder_withCardPayment_setsPaymentStatusToPaid() {
        OrderRequest request = createSingleItemRequest(PaymentMethod.CARD_PAYMENT);
        setupSingleItemCreateMocks(request);
        orderService.createOrder(request);
        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();
        assertThat(saved.getPaymentMethod()).isEqualTo(PaymentMethod.CARD_PAYMENT);
        assertThat(saved.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(saved.getPaidAmount()).isEqualByComparingTo(new BigDecimal("2000.00"));
        assertThat(saved.getOutstandingBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void createOrder_withBankTransfer_setsPaymentStatusToPaid() {
        OrderRequest request = createSingleItemRequest(PaymentMethod.BANK_TRANSFER);
        setupSingleItemCreateMocks(request);
        orderService.createOrder(request);
        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();
        assertThat(saved.getPaymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(saved.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(saved.getPaidAmount()).isEqualByComparingTo(new BigDecimal("2000.00"));
    }

    @Test
    void createOrder_withCod_setsPaymentStatusToUnpaid() {
        OrderRequest request = createSingleItemRequest(PaymentMethod.COD);
        setupSingleItemCreateMocks(request);
        orderService.createOrder(request);
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
        OrderRequest request = createSingleItemRequest(PaymentMethod.CREDIT);
        request.setPaidAmount(new BigDecimal("2000.00"));
        setupSingleItemCreateMocks(request);
        orderService.createOrder(request);
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
        OrderRequest request = createSingleItemRequest(PaymentMethod.CREDIT);
        request.setPaidAmount(new BigDecimal("1000.00"));
        setupSingleItemCreateMocks(request);
        orderService.createOrder(request);
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
        OrderRequest request = createSingleItemRequest(PaymentMethod.CREDIT);
        setupSingleItemCreateMocks(request);
        orderService.createOrder(request);
        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();
        assertThat(saved.getPaymentMethod()).isEqualTo(PaymentMethod.CREDIT);
        assertThat(saved.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(saved.getPaidAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getOutstandingBalance()).isEqualByComparingTo(new BigDecimal("2000.00"));
        assertThat(saved.getPaidAt()).isNull();
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
    void updateOrderStatus_canSkipForwardSteps() {
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderMapper.toResponse(any())).thenReturn(new OrderResponse());

        orderService.updateOrderStatus(order.getId(), OrderStatus.SHIPPED);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(order.getDeliveryStatus()).isEqualTo(com.ebee.delivery.enums.DeliveryStatus.PENDING);
    }

    @Test
    void updateOrderStatus_goingBackwards_isRejected() {
        order.setStatus(OrderStatus.SHIPPED);
        order.setDeliveryStatus(com.ebee.delivery.enums.DeliveryStatus.ASSIGNED);
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateOrderStatus(order.getId(), OrderStatus.PENDING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only moves forward");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void updateOrderStatus_statusesWithNoManualStep_areRejected() {
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateOrderStatus(order.getId(), OrderStatus.FAILED))
                .hasMessageContaining("can't be set manually");
    }

    @Test
    void updateOrderStatus_reshipWithClearedDelivery_isStillAllowed() {
        order.setStatus(OrderStatus.SHIPPED);
        order.setDeliveryStatus(null);
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderMapper.toResponse(any())).thenReturn(new OrderResponse());

        orderService.updateOrderStatus(order.getId(), OrderStatus.SHIPPED);

        assertThat(order.getDeliveryStatus()).isEqualTo(com.ebee.delivery.enums.DeliveryStatus.PENDING);
    }

    @Test
    void returnOrder_notYetDelivered_isRejected_cancelInstead() {
        order.setStatus(OrderStatus.SHIPPED);
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.returnOrder(order.getId(),
                new ReturnOrderItemRequest(null, RefundChoice.REFUND, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only delivered orders can be returned");
        verify(inventoryService, never()).returnStock(any(), anyList(), anyList(), anyList(), anyList(), anyList());
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
                .hasMessageContaining("Use delivery or cancel endpoints after shipping");
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
    void updateOrderStatus_shipped_failedPermanentOrder_throws() {
        UUID orderId = order.getId();
        order.setStatus(OrderStatus.CANCELLED);
        order.setDeliveryStatus(DeliveryStatus.FAILED_PERMANENT);

        when(orderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateOrderStatus(orderId, OrderStatus.SHIPPED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot update status of a cancelled order");
    }

    @Test
    void updateOrderStatus_shipped_keepsInFlightDelivery() {
        UUID orderId = order.getId();
        order.setStatus(OrderStatus.CONFIRMED);
        order.setDeliveryStatus(DeliveryStatus.IN_TRANSIT);
        Trip trip = Trip.builder().id(UUID.randomUUID()).name("Route").build();
        order.setTrip(trip);
        order.setDeliveryAssignedTo(adminUser);

        when(orderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderMapper.toResponse(any())).thenReturn(new OrderResponse());

        orderService.updateOrderStatus(orderId, OrderStatus.SHIPPED);

        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();
        assertThat(saved.getDeliveryStatus()).isEqualTo(DeliveryStatus.IN_TRANSIT);
        assertThat(saved.getTrip()).isEqualTo(trip);
        assertThat(saved.getDeliveryAssignedTo()).isEqualTo(adminUser);
    }

    @Test
    void cancelOrder_success() {
        UUID orderId = order.getId();
        when(orderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderMapper.toResponse(any())).thenReturn(new OrderResponse());

        orderService.cancelOrder(orderId, new OrderSettlementRequest(RefundChoice.REFUND, null));

        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        // Unpaid cancellation must reverse the invoice, or the customer keeps owing for it.
        verify(ledgerService).recordCustomerCreditNote(eq(customer.getId()), eq(orderId),
                argThat(a -> a.compareTo(order.getTotalAmount()) == 0), anyString(), any());
        verify(ledgerService, never()).recordCustomerRefund(any(), any(), any(), any(), any());
        assertThat(saved.getPaidAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getOutstandingBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        verify(inventoryService).returnStock(any(), anyList(), anyList(), anyList(), anyList(), anyList());
        verify(commissionLifecycleService).cancelCommissionForOrder(orderId);
        verify(auditLogService).record(eq(AuditAction.STATUS_CHANGE), eq("Order"), eq(orderId), anyString());
        verify(orderNotificationService).sendOrderCancelled(any());
    }

    @Test
    void cancelOrder_alreadyCancelled_throws() {
        order.setStatus(OrderStatus.CANCELLED);
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(order.getId(), any()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Order is already cancelled");
    }

    @Test
    void cancelOrder_shipped_throws() {
        order.setStatus(OrderStatus.SHIPPED);
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(order.getId(), any()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot cancel a shipped order");
    }

    @Test
    void cancelOrder_delivered_throws() {
        order.setStatus(OrderStatus.DELIVERED);
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(order.getId(), any()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot cancel a delivered order");
    }

    @Test
    void cancelOrder_deliveryFailedPermanent_alreadyCancelled() {
        // When delivery becomes FAILED_PERMANENT, the state machine sets order
        // status to CANCELLED. Calling cancelOrder again is a no-op (already cancelled).
        order.setStatus(OrderStatus.CANCELLED);
        order.setDeliveryStatus(DeliveryStatus.FAILED_PERMANENT);
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(order.getId(), any()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Order is already cancelled");
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

        orderService.cancelOrder(orderId, new OrderSettlementRequest(RefundChoice.REFUND, null));

        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();
        assertThat(saved.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(saved.getPaidAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getOutstandingBalance()).isEqualByComparingTo(BigDecimal.ZERO);
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

        orderService.cancelOrder(order.getId(), new OrderSettlementRequest(RefundChoice.CREDIT_ADVANCE, null));

        // Nothing was paid: only the invoice is reversed, no money moves to the advance wallet.
        verify(ledgerService).recordCustomerCreditNote(eq(customer.getId()), eq(order.getId()),
                argThat(a -> a.compareTo(new BigDecimal("6500.00")) == 0), anyString(), any());
        verify(ledgerService, never()).recordCustomerAdvanceTransfer(any(), any(), any(), any(), any());
        verify(customerService, never()).creditToAdvance(any(), any(), any(), any(), any());
        verify(customerService).syncAllBalances(customer);
        assertThat(order.getOutstandingBalance()).isEqualByComparingTo(BigDecimal.ZERO);
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

        orderService.cancelOrder(order.getId(), new OrderSettlementRequest(RefundChoice.REFUND, null));

        verify(ledgerService).recordCustomerCreditNote(eq(customer.getId()), eq(order.getId()),
                argThat(a -> a.compareTo(order.getTotalAmount()) == 0), anyString(), any());
        verify(ledgerService).recordCustomerRefund(eq(customer.getId()), eq(order.getId()),
                argThat(a -> a.compareTo(new BigDecimal("2500.00")) == 0), anyString(), any());
        verify(customerService).syncAllBalances(customer);
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
        when(securityService.getCurrentUserId()).thenReturn(UUID.randomUUID());

        ReturnOrderItemRequest request = new ReturnOrderItemRequest(
                null, RefundChoice.REFUND, null);
        orderService.returnOrder(orderId, request);

        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.RETURNED);
        verify(inventoryService).returnStock(any(), anyList(), anyList(), anyList(), anyList(), anyList());
        verify(commissionLifecycleService).adjustCommissionForOrder(order);
        verify(orderNotificationService).sendOrderStatus(any());
    }

    @Test
    void returnOrder_cancelled_throws() {
        order.setStatus(OrderStatus.CANCELLED);
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));

        ReturnOrderItemRequest request = new ReturnOrderItemRequest(null, RefundChoice.REFUND, null);
        assertThatThrownBy(() -> orderService.returnOrder(order.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot return an order that is already");
    }

    @Test
    void returnOrder_alreadyReturned_throws() {
        order.setStatus(OrderStatus.RETURNED);
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));

        ReturnOrderItemRequest request = new ReturnOrderItemRequest(null, RefundChoice.REFUND, null);
        assertThatThrownBy(() -> orderService.returnOrder(order.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot return an order that is already");
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
    void createOrder_discountValueWithoutDiscountType_throws() {
        Product product = Product.builder()
                .id(UUID.randomUUID())
                .itemCode("MIXED-001")
                .name("Mixed Pair Item")
                .price(new BigDecimal("500.00"))
                .maxDiscountType("PERCENTAGE")
                .maxDiscountValue(new BigDecimal("10"))
                .isActive(true)
                .build();

        OrderItemRequest item = new OrderItemRequest();
        item.setProductItemCode("MIXED-001");
        item.setQuantity(1);
        item.setDiscountValue(new BigDecimal("5"));

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
                .thenReturn("JF-A-20250614-008");
        when(productRepository.findByItemCodeIn(anyList()))
                .thenReturn(List.of(product));

        assertThatThrownBy(() -> orderService.createOrder(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("discountType and discountValue must both be provided together");
    }

    @Test
    void createOrder_discountTypeWithoutDiscountValue_throws() {
        Product product = Product.builder()
                .id(UUID.randomUUID())
                .itemCode("MIXED-002")
                .name("Mixed Pair Item Two")
                .price(new BigDecimal("500.00"))
                .maxDiscountType("PERCENTAGE")
                .maxDiscountValue(new BigDecimal("10"))
                .isActive(true)
                .build();

        OrderItemRequest item = new OrderItemRequest();
        item.setProductItemCode("MIXED-002");
        item.setQuantity(1);
        item.setDiscountType(com.ebee.common.enums.DiscountType.PERCENTAGE);

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
                .thenReturn("JF-A-20250614-009");
        when(productRepository.findByItemCodeIn(anyList()))
                .thenReturn(List.of(product));

        assertThatThrownBy(() -> orderService.createOrder(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("discountType and discountValue must both be provided together");
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

    @Test
    void createOrder_recordsInvoiceInLedger() {
        when(securityService.isAdmin()).thenReturn(true);
        when(agentRepository.findByAgentCode("JF-008")).thenReturn(Optional.of(agent));
        when(securityService.getCurrentUserId()).thenReturn(adminUserId);
        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));
        when(customerService.findByPhone("0771234567")).thenReturn(customer);
        when(orderNumberService.generateOrderNumber(District.JF, OrderSource.AGENT))
                .thenReturn("JF-A-20250614-001");
        when(productRepository.findByItemCodeIn(anyList()))
                .thenReturn(List.of(product1, product2));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            if (o.getId() == null) {
                o.setId(UUID.randomUUID());
            }
            return o;
        });
        when(commissionCalculationService.calculateCommissionForOrder(any()))
                .thenReturn(Commission.builder()
                        .id(UUID.randomUUID())
                        .commissionAmount(new BigDecimal("487.50"))
                        .commissionRate(new BigDecimal("7.5"))
                        .status(CommissionStatus.CALCULATED)
                        .build());
        when(orderMapper.toResponse(any())).thenReturn(new OrderResponse());

        orderService.createOrder(orderRequest);

        verify(ledgerService).recordCustomerInvoice(
                eq(customer.getId()),
                any(UUID.class),
                any(BigDecimal.class),
                contains("Order"),
                any(UUID.class)
        );
    }

    @Test
    void returnOrder_recordsRefundInLedger() {
        order.setStatus(OrderStatus.DELIVERED);
        order.setPaymentMethod(PaymentMethod.CASH);
        order.setPaidAmount(new BigDecimal("6500.00"));
        order.setOutstandingBalance(BigDecimal.ZERO);
        order.setPaymentStatus(PaymentStatus.PAID);

        UUID orderId = order.getId();
        when(orderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderMapper.toResponse(any())).thenReturn(new OrderResponse());
        when(securityService.getCurrentUserId()).thenReturn(UUID.randomUUID());

        ReturnOrderItemRequest request = new ReturnOrderItemRequest(null, RefundChoice.REFUND, null);
        orderService.returnOrder(orderId, request);

        verify(ledgerService).recordCustomerCreditNote(eq(customer.getId()), eq(orderId),
                argThat(a -> a.compareTo(new BigDecimal("6500.00")) == 0), anyString(), any(UUID.class));
        verify(ledgerService).recordCustomerRefund(
                eq(customer.getId()),
                eq(orderId),
                argThat(a -> a.compareTo(new BigDecimal("6500.00")) == 0),
                contains("return"),
                any(UUID.class)
        );
    }
}
