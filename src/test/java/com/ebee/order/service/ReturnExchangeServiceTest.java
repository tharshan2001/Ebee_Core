package com.ebee.order.service;

import com.ebee.accounting.service.LedgerService;
import com.ebee.audit.service.AuditLogService;
import com.ebee.commission.service.CommissionLifecycleService;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.security.SecurityService;
import com.ebee.customer.entity.Customer;
import com.ebee.customer.repository.CustomerRepository;
import com.ebee.inventory.service.InventoryService;
import com.ebee.order.dto.*;
import com.ebee.order.entity.Order;
import com.ebee.order.entity.OrderItem;
import com.ebee.order.entity.ReturnExchangeRequest;
import com.ebee.order.entity.ReturnRequestItem;
import com.ebee.order.enums.*;
import com.ebee.order.repository.OrderRepository;
import com.ebee.order.repository.ReturnExchangeRepository;
import com.ebee.product.entity.Product;
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
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReturnExchangeServiceTest {

    @Mock private ReturnExchangeRepository returnExchangeRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private UserRepository userRepository;
    @Mock private InventoryService inventoryService;
    @Mock private LedgerService ledgerService;
    @Mock private CommissionLifecycleService commissionLifecycleService;
    @Mock private OrderNotificationService orderNotificationService;
    @Mock private OrderPaymentService orderPaymentService;
    @Mock private AuditLogService auditLogService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private SecurityService securityService;
    @Mock private com.ebee.customer.service.CustomerService customerService;

    @InjectMocks private ReturnExchangeService returnExchangeService;

    @Captor private ArgumentCaptor<ReturnExchangeRequest> returnRequestCaptor;

    private UUID adminUserId;
    private User adminUser;
    private Customer customer;
    private Product product1;
    private Product product2;
    private OrderItem orderItem1;
    private OrderItem orderItem2;
    private Order order;

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(returnExchangeService, "orderSettlementService",
                new OrderSettlementService(inventoryService, ledgerService, customerService, securityService));
        adminUserId = UUID.randomUUID();
        adminUser = User.builder()
                .id(adminUserId)
                .email("admin@ebee.com")
                .firstName("Admin")
                .lastName("User")
                .role(RoleType.ADMIN)
                .build();

        customer = Customer.builder()
                .id(UUID.randomUUID())
                .firstName("John")
                .lastName("Doe")
                .phoneNumber("0771234567")
                .outstandingBalance(BigDecimal.ZERO)
                .advanceBalance(BigDecimal.ZERO)
                .creditLimit(BigDecimal.ZERO)
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

        orderItem1 = OrderItem.builder()
                .id(UUID.randomUUID())
                .product(product1)
                .quantity(3)
                .unitPrice(new BigDecimal("2500.00"))
                .subtotal(new BigDecimal("7500.00"))
                .returnedQuantity(0)
                .build();

        orderItem2 = OrderItem.builder()
                .id(UUID.randomUUID())
                .product(product2)
                .quantity(2)
                .unitPrice(new BigDecimal("1500.00"))
                .subtotal(new BigDecimal("3000.00"))
                .returnedQuantity(0)
                .build();

        order = Order.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .createdBy(adminUser)
                .orderNumber("JF-A-20250614-001")
                .status(OrderStatus.DELIVERED)
                .totalAmount(new BigDecimal("10500.00"))
                .paidAmount(new BigDecimal("10500.00"))
                .outstandingBalance(BigDecimal.ZERO)
                .paymentStatus(PaymentStatus.PAID)
                .items(List.of(orderItem1, orderItem2))
                .build();

        orderItem1.setOrder(order);
        orderItem2.setOrder(order);
    }

    @Test
    void initiateReturnExchange_success() {
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(returnExchangeRepository.existsActiveByOrderId(order.getId())).thenReturn(false);
        when(returnExchangeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InitiateReturnExchangeRequest request = new InitiateReturnExchangeRequest(
                List.of(new ReturnItemRequest(orderItem1.getId(), 2)),
                "Defective product"
        );

        ReturnExchangeResponse response = returnExchangeService.initiateReturnExchange(order.getId(), request);

        verify(returnExchangeRepository).save(returnRequestCaptor.capture());
        ReturnExchangeRequest saved = returnRequestCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ReturnExchangeStatus.PENDING_REVIEW);
        assertThat(saved.getRequestedAmount()).isEqualByComparingTo(new BigDecimal("5000.00"));
        assertThat(saved.getItems()).hasSize(1);
        assertThat(saved.getItems().get(0).getQuantity()).isEqualTo(2);
    }

    @Test
    void initiateReturnExchange_multipleItems() {
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(returnExchangeRepository.existsActiveByOrderId(order.getId())).thenReturn(false);
        when(returnExchangeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InitiateReturnExchangeRequest request = new InitiateReturnExchangeRequest(
                List.of(
                        new ReturnItemRequest(orderItem1.getId(), 1),
                        new ReturnItemRequest(orderItem2.getId(), 2)
                ),
                "Wrong items delivered"
        );

        ReturnExchangeResponse response = returnExchangeService.initiateReturnExchange(order.getId(), request);

        verify(returnExchangeRepository).save(returnRequestCaptor.capture());
        ReturnExchangeRequest saved = returnRequestCaptor.getValue();
        assertThat(saved.getRequestedAmount()).isEqualByComparingTo(new BigDecimal("5500.00"));
        assertThat(saved.getItems()).hasSize(2);
    }

    @Test
    void initiateReturnExchange_nonDelivered_throws() {
        order.setStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));

        InitiateReturnExchangeRequest request = new InitiateReturnExchangeRequest(
                List.of(new ReturnItemRequest(orderItem1.getId(), 1)),
                "Reason"
        );

        assertThatThrownBy(() -> returnExchangeService.initiateReturnExchange(order.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DELIVERED");
    }

    @Test
    void initiateReturnExchange_exceedsReturnable_throws() {
        orderItem1.setReturnedQuantity(2);
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(returnExchangeRepository.existsActiveByOrderId(order.getId())).thenReturn(false);

        InitiateReturnExchangeRequest request = new InitiateReturnExchangeRequest(
                List.of(new ReturnItemRequest(orderItem1.getId(), 2)),
                "Reason"
        );

        assertThatThrownBy(() -> returnExchangeService.initiateReturnExchange(order.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Returnable");
    }

    @Test
    void initiateReturnExchange_duplicateActive_throws() {
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(returnExchangeRepository.existsActiveByOrderId(order.getId())).thenReturn(true);

        InitiateReturnExchangeRequest request = new InitiateReturnExchangeRequest(
                List.of(new ReturnItemRequest(orderItem1.getId(), 1)),
                "Reason"
        );

        assertThatThrownBy(() -> returnExchangeService.initiateReturnExchange(order.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already requested");
    }

    @Test
    void initiateReturnExchange_resubmitAfterRejected_success() {
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(returnExchangeRepository.existsActiveByOrderId(order.getId())).thenReturn(false);
        when(returnExchangeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InitiateReturnExchangeRequest request = new InitiateReturnExchangeRequest(
                List.of(new ReturnItemRequest(orderItem1.getId(), 1)),
                "Retry"
        );

        ReturnExchangeResponse response = returnExchangeService.initiateReturnExchange(order.getId(), request);
        assertThat(response).isNotNull();
    }

    @Test
    void reviewReturnExchange_advanceApproved() {
        ReturnExchangeRequest returnRequest = buildReturnRequest();
        when(returnExchangeRepository.findById(returnRequest.getId())).thenReturn(Optional.of(returnRequest));
        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(returnExchangeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(customerService.creditToAdvance(any(), any(), any(), any(), any()))
                .thenReturn(com.ebee.customer.entity.CustomerAdvanceTransaction.builder()
                        .id(UUID.randomUUID()).build());

        ReviewReturnExchangeRequest reviewRequest = new ReviewReturnExchangeRequest(
                new BigDecimal("5000.00"),
                ReturnExchangeResolution.ADVANCE,
                null
        );

        ReturnExchangeResponse response = returnExchangeService.reviewReturnExchange(
                returnRequest.getId(), reviewRequest, adminUserId);

        assertThat(returnRequest.getStatus()).isEqualTo(ReturnExchangeStatus.APPROVED);
        verify(inventoryService).recordOrderStocks(anyList(), anyList(), eq(StockMovementType.RETURN), anyList());
        verify(commissionLifecycleService).cancelCommissionForOrder(order.getId());
        verify(customerService).creditToAdvance(eq(customer), any(), any(), eq(order.getId()), any());
        verify(orderNotificationService).sendOrderStatus(any());
        verify(eventPublisher, atLeast(2)).publishEvent(any());
    }

    @Test
    void reviewReturnExchange_cashApproved() {
        order.setPaidAmount(new BigDecimal("5000.00"));
        ReturnExchangeRequest returnRequest = buildReturnRequest();
        when(returnExchangeRepository.findById(returnRequest.getId())).thenReturn(Optional.of(returnRequest));
        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(returnExchangeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReviewReturnExchangeRequest reviewRequest = new ReviewReturnExchangeRequest(
                new BigDecimal("2500.00"),
                ReturnExchangeResolution.CASH,
                null
        );

        returnExchangeService.reviewReturnExchange(returnRequest.getId(), reviewRequest, adminUserId);

        assertThat(returnRequest.getStatus()).isEqualTo(ReturnExchangeStatus.APPROVED);
        verify(inventoryService).recordOrderStocks(anyList(), anyList(), eq(StockMovementType.RETURN), anyList());
        verify(commissionLifecycleService).cancelCommissionForOrder(order.getId());
    }

    @Test
    void reviewReturnExchange_partialItemsApproved() {
        ReturnExchangeRequest returnRequest = buildReturnRequest();
        when(returnExchangeRepository.findById(returnRequest.getId())).thenReturn(Optional.of(returnRequest));
        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(returnExchangeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(customerService.creditToAdvance(any(), any(), any(), any(), any()))
                .thenReturn(com.ebee.customer.entity.CustomerAdvanceTransaction.builder()
                        .id(UUID.randomUUID()).build());

        ReviewReturnExchangeRequest reviewRequest = new ReviewReturnExchangeRequest(
                new BigDecimal("2500.00"),
                ReturnExchangeResolution.ADVANCE,
                null
        );

        returnExchangeService.reviewReturnExchange(returnRequest.getId(), reviewRequest, adminUserId);

        assertThat(orderItem1.getReturnedQuantity()).isEqualTo(1);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void reviewReturnExchange_allItemsReturned_statusReturned() {
        orderItem1.setQuantity(1);
        orderItem1.setSubtotal(new BigDecimal("2500.00"));
        orderItem2.setReturnedQuantity(2);
        ReturnExchangeRequest returnRequest = buildReturnRequest();
        when(returnExchangeRepository.findById(returnRequest.getId())).thenReturn(Optional.of(returnRequest));
        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(returnExchangeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(customerService.creditToAdvance(any(), any(), any(), any(), any()))
                .thenReturn(com.ebee.customer.entity.CustomerAdvanceTransaction.builder()
                        .id(UUID.randomUUID()).build());

        ReviewReturnExchangeRequest reviewRequest = new ReviewReturnExchangeRequest(
                new BigDecimal("2500.00"),
                ReturnExchangeResolution.ADVANCE,
                null
        );

        returnExchangeService.reviewReturnExchange(returnRequest.getId(), reviewRequest, adminUserId);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.RETURNED);
    }

    @Test
    void reviewReturnExchange_orderTotalReduced() {
        ReturnExchangeRequest returnRequest = buildReturnRequest();
        when(returnExchangeRepository.findById(returnRequest.getId())).thenReturn(Optional.of(returnRequest));
        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(returnExchangeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(customerService.creditToAdvance(any(), any(), any(), any(), any()))
                .thenReturn(com.ebee.customer.entity.CustomerAdvanceTransaction.builder()
                        .id(UUID.randomUUID()).build());

        ReviewReturnExchangeRequest reviewRequest = new ReviewReturnExchangeRequest(
                new BigDecimal("2500.00"),
                ReturnExchangeResolution.ADVANCE,
                null
        );

        returnExchangeService.reviewReturnExchange(returnRequest.getId(), reviewRequest, adminUserId);

        assertThat(order.getTotalAmount()).isEqualByComparingTo(new BigDecimal("8000.00"));
    }

    @Test
    void reviewReturnExchange_commissionCancelled() {
        ReturnExchangeRequest returnRequest = buildReturnRequest();
        when(returnExchangeRepository.findById(returnRequest.getId())).thenReturn(Optional.of(returnRequest));
        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(returnExchangeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(customerService.creditToAdvance(any(), any(), any(), any(), any()))
                .thenReturn(com.ebee.customer.entity.CustomerAdvanceTransaction.builder()
                        .id(UUID.randomUUID()).build());

        ReviewReturnExchangeRequest reviewRequest = new ReviewReturnExchangeRequest(
                new BigDecimal("2500.00"),
                ReturnExchangeResolution.ADVANCE,
                null
        );

        returnExchangeService.reviewReturnExchange(returnRequest.getId(), reviewRequest, adminUserId);

        verify(commissionLifecycleService).cancelCommissionForOrder(order.getId());
    }

    @Test
    void reviewReturnExchange_notificationSent() {
        ReturnExchangeRequest returnRequest = buildReturnRequest();
        when(returnExchangeRepository.findById(returnRequest.getId())).thenReturn(Optional.of(returnRequest));
        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(returnExchangeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(customerService.creditToAdvance(any(), any(), any(), any(), any()))
                .thenReturn(com.ebee.customer.entity.CustomerAdvanceTransaction.builder()
                        .id(UUID.randomUUID()).build());

        ReviewReturnExchangeRequest reviewRequest = new ReviewReturnExchangeRequest(
                new BigDecimal("2500.00"),
                ReturnExchangeResolution.ADVANCE,
                null
        );

        returnExchangeService.reviewReturnExchange(returnRequest.getId(), reviewRequest, adminUserId);

        verify(orderNotificationService).sendOrderStatus(any());
    }

    @Test
    void reviewReturnExchange_rejected() {
        ReturnExchangeRequest returnRequest = buildReturnRequest();
        when(returnExchangeRepository.findById(returnRequest.getId())).thenReturn(Optional.of(returnRequest));
        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));
        when(returnExchangeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReviewReturnExchangeRequest reviewRequest = new ReviewReturnExchangeRequest(
                new BigDecimal("2500.00"),
                ReturnExchangeResolution.REJECTED,
                "Not eligible"
        );

        ReturnExchangeResponse response = returnExchangeService.reviewReturnExchange(
                returnRequest.getId(), reviewRequest, adminUserId);

        assertThat(returnRequest.getStatus()).isEqualTo(ReturnExchangeStatus.REJECTED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(orderItem1.getReturnedQuantity()).isEqualTo(0);
        verify(inventoryService, never()).recordOrderStocks(anyList(), anyList(), any(), anyList());
        verify(commissionLifecycleService, never()).cancelCommissionForOrder(any());
    }

    @Test
    void reviewReturnExchange_accountingPeriodTransaction() {
        ReturnExchangeRequest returnRequest = buildReturnRequest();
        when(returnExchangeRepository.findById(returnRequest.getId())).thenReturn(Optional.of(returnRequest));
        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(returnExchangeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(customerService.creditToAdvance(any(), any(), any(), any(), any()))
                .thenReturn(com.ebee.customer.entity.CustomerAdvanceTransaction.builder()
                        .id(UUID.randomUUID()).build());

        ReviewReturnExchangeRequest reviewRequest = new ReviewReturnExchangeRequest(
                new BigDecimal("2500.00"),
                ReturnExchangeResolution.ADVANCE,
                null
        );

        returnExchangeService.reviewReturnExchange(returnRequest.getId(), reviewRequest, adminUserId);

        verify(customerService).creditToAdvance(eq(customer), any(), any(), eq(order.getId()), any());
    }

    @Test
    void reviewReturnExchange_nullCustomer_throws() {
        order.setCustomer(null);
        ReturnExchangeRequest returnRequest = buildReturnRequest();
        when(returnExchangeRepository.findById(returnRequest.getId())).thenReturn(Optional.of(returnRequest));
        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));

        ReviewReturnExchangeRequest reviewRequest = new ReviewReturnExchangeRequest(
                new BigDecimal("2500.00"),
                ReturnExchangeResolution.ADVANCE,
                null
        );

        assertThatThrownBy(() -> returnExchangeService.reviewReturnExchange(
                returnRequest.getId(), reviewRequest, adminUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no customer");
    }

    @Test
    void reviewReturnExchange_notPendingReview_throws() {
        ReturnExchangeRequest returnRequest = buildReturnRequest();
        returnRequest.setStatus(ReturnExchangeStatus.APPROVED);
        when(returnExchangeRepository.findById(returnRequest.getId())).thenReturn(Optional.of(returnRequest));

        ReviewReturnExchangeRequest reviewRequest = new ReviewReturnExchangeRequest(
                new BigDecimal("2500.00"),
                ReturnExchangeResolution.ADVANCE,
                null
        );

        assertThatThrownBy(() -> returnExchangeService.reviewReturnExchange(
                returnRequest.getId(), reviewRequest, adminUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not pending review");
    }

    @Test
    void getPendingReviews_success() {
        ReturnExchangeRequest returnRequest = buildReturnRequest();
        when(returnExchangeRepository.findByStatusOrderByCreatedAtDesc(ReturnExchangeStatus.PENDING_REVIEW))
                .thenReturn(List.of(returnRequest));

        List<ReturnExchangeResponse> results = returnExchangeService.getPendingReviews();

        assertThat(results).hasSize(1);
    }

    @Test
    void getByOrderId_success() {
        ReturnExchangeRequest returnRequest = buildReturnRequest();
        when(returnExchangeRepository.findByOrderId(order.getId())).thenReturn(Optional.of(returnRequest));

        ReturnExchangeResponse response = returnExchangeService.getByOrderId(order.getId());

        assertThat(response.orderId()).isEqualTo(order.getId());
    }

    @Test
    void getByOrderId_notFound_throws() {
        when(returnExchangeRepository.findByOrderId(order.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> returnExchangeService.getByOrderId(order.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void countPending_success() {
        when(returnExchangeRepository.countByStatus(ReturnExchangeStatus.PENDING_REVIEW)).thenReturn(5L);

        long count = returnExchangeService.countPending();

        assertThat(count).isEqualTo(5L);
    }

    private ReturnExchangeRequest buildReturnRequest() {
        ReturnRequestItem rri = ReturnRequestItem.builder()
                .id(UUID.randomUUID())
                .orderItem(orderItem1)
                .quantity(1)
                .unitRefundAmount(new BigDecimal("2500.00"))
                .subtotal(new BigDecimal("2500.00"))
                .build();

        ReturnExchangeRequest returnRequest = ReturnExchangeRequest.builder()
                .id(UUID.randomUUID())
                .order(order)
                .requestedAmount(new BigDecimal("2500.00"))
                .status(ReturnExchangeStatus.PENDING_REVIEW)
                .reason("Defective product")
                .items(List.of(rri))
                .build();

        rri.setReturnRequest(returnRequest);
        return returnRequest;
    }
}
