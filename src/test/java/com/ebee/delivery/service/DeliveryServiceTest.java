package com.ebee.delivery.service;

import com.ebee.audit.enums.AuditAction;
import com.ebee.audit.service.AuditLogService;
import com.ebee.common.enums.District;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.security.SecurityService;
import com.ebee.commission.service.CommissionLifecycleService;
import com.ebee.customer.entity.Address;
import com.ebee.customer.entity.Customer;
import com.ebee.delivery.dto.DeliveryResponse;
import com.ebee.delivery.dto.DeliveryStatusUpdateRequest;
import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.delivery.repository.DeliveryRepository;
import com.ebee.driver.entity.Driver;
import com.ebee.driver.enums.DriverAvailability;
import com.ebee.driver.repository.DriverRepository;
import com.ebee.inventory.service.InventoryService;
import com.ebee.notification.NotificationPort;
import com.ebee.order.entity.Order;
import com.ebee.order.entity.OrderItem;
import com.ebee.order.enums.OrderStatus;
import com.ebee.order.repository.OrderRepository;
import com.ebee.product.entity.Product;
import com.ebee.stock.enums.StockMovementType;
import com.ebee.user.entity.User;
import com.ebee.user.enums.RoleType;
import com.ebee.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceTest {

    @Mock private DeliveryRepository deliveryRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private UserRepository userRepository;
    @Mock private DriverRepository driverRepository;
    @Mock private SecurityService securityService;
    @Mock private NotificationPort notificationPort;
    @Mock private InventoryService inventoryService;
    @Mock private CommissionLifecycleService commissionLifecycleService;
    @Mock private AuditLogService auditLogService;
    @Mock private DeliveryStateTransition deliveryStateTransition;

    @InjectMocks private DeliveryService deliveryService;

    private UUID orderId;
    private UUID driverUserId;
    private Order order;
    private Customer customer;
    private User driverUser;
    private Driver driverProfile;
    private Product product;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        driverUserId = UUID.randomUUID();

        Address address = Address.builder()
                .id(UUID.randomUUID())
                .district(District.JF)
                .build();

        customer = Customer.builder()
                .id(UUID.randomUUID())
                .firstName("John")
                .lastName("Doe")
                .phoneNumber("0771234567")
                .address(address)
                .build();

        driverUser = User.builder()
                .id(driverUserId)
                .email("driver@ebee.com")
                .firstName("Driver")
                .lastName("User")
                .role(RoleType.DRIVER)
                .build();

        driverProfile = Driver.builder()
                .id(UUID.randomUUID())
                .user(driverUser)
                .availability(DriverAvailability.AVAILABLE)
                .build();

        product = Product.builder()
                .id(UUID.randomUUID())
                .name("Test Product")
                .itemCode("PROD-001")
                .price(new BigDecimal("1000.00"))
                .isActive(true)
                .build();

        order = Order.builder()
                .id(orderId)
                .orderNumber("JF-A-20250614-001")
                .customer(customer)
                .status(OrderStatus.SHIPPED)
                .deliveryStatus(DeliveryStatus.PENDING)
                .totalAmount(new BigDecimal("1000.00"))
                .items(List.of(
                        OrderItem.builder()
                                .id(UUID.randomUUID())
                                .product(product)
                                .quantity(1)
                                .unitPrice(new BigDecimal("1000.00"))
                                .subtotal(new BigDecimal("1000.00"))
                                .build()
                ))
                .build();
    }

    @Test
    void initializeDelivery_setsPending() {
        Order newOrder = Order.builder().id(UUID.randomUUID()).build();
        assertThat(newOrder.getDeliveryStatus()).isNull();

        deliveryService.initializeDelivery(newOrder);

        assertThat(newOrder.getDeliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
    }

    @Test
    void assignDelivery_success() {
        order.setDeliveryStatus(DeliveryStatus.PENDING);

        when(orderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.of(order));
        when(userRepository.findById(driverUserId)).thenReturn(Optional.of(driverUser));
        when(driverRepository.findByUserId(driverUserId)).thenReturn(Optional.of(driverProfile));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = deliveryService.assignDelivery(orderId, driverUserId);

        assertThat(result).isNotNull();
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
        assertThat(order.getDeliveryAssignedTo()).isEqualTo(driverUser);
        assertThat(driverProfile.getAvailability()).isEqualTo(DriverAvailability.ON_TRIP);
        verify(driverRepository).save(driverProfile);
        verify(notificationPort).notifyUser(eq(driverUser), anyString(), eq("delivery-assigned-driver"), anyMap(), anySet());
        verify(notificationPort).createInternalAlert(eq(driverUser), anyString(), anyString(), any(), eq(orderId), eq("order"));
        verify(notificationPort).notifyCustomer(eq(customer), anyString(), eq("delivery-assigned-customer"), anyMap(), anySet());
        verify(auditLogService).record(eq(AuditAction.DELIVERY_ASSIGN), eq("Order"), eq(orderId), anyString());
    }

    @Test
    void assignDelivery_notPending_throws() {
        order.setDeliveryStatus(DeliveryStatus.ASSIGNED);

        when(orderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> deliveryService.assignDelivery(orderId, driverUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Delivery can only be assigned when status is PENDING");
    }

    @Test
    void assignDelivery_userNotDriver_throws() {
        UUID adminId = UUID.randomUUID();
        User adminUser = User.builder().id(adminId).role(RoleType.ADMIN).build();
        order.setDeliveryStatus(DeliveryStatus.PENDING);

        when(orderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.of(order));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));

        assertThatThrownBy(() -> deliveryService.assignDelivery(orderId, adminId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User is not a driver");
    }

    @Test
    void assignDelivery_driverSuspended_throws() {
        driverProfile.setAvailability(DriverAvailability.SUSPENDED);
        order.setDeliveryStatus(DeliveryStatus.PENDING);

        when(orderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.of(order));
        when(userRepository.findById(driverUserId)).thenReturn(Optional.of(driverUser));
        when(driverRepository.findByUserId(driverUserId)).thenReturn(Optional.of(driverProfile));

        assertThatThrownBy(() -> deliveryService.assignDelivery(orderId, driverUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("driver is suspended");
    }

    @Test
    void updateDeliveryStatus_driverCanUpdateOwnDelivery() {
        order.setDeliveryAssignedTo(driverUser);
        order.setDeliveryStatus(DeliveryStatus.ASSIGNED);
        DeliveryStatusUpdateRequest request = new DeliveryStatusUpdateRequest();
        request.setStatus(DeliveryStatus.PICKED_UP);

        when(orderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.of(order));
        when(securityService.isAdmin()).thenReturn(false);
        when(securityService.getCurrentUserId()).thenReturn(driverUserId);
        when(deliveryStateTransition.transition(order, DeliveryStatus.PICKED_UP, null, false))
                .thenReturn(new DeliveryStateTransition.TransitionResult(false, false, DeliveryStatus.ASSIGNED));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = deliveryService.updateDeliveryStatus(orderId, request);

        assertThat(result).isNotNull();
        verify(auditLogService).record(eq(AuditAction.DELIVERY_STATUS_CHANGE), eq("Order"), eq(orderId), anyString());
    }

    @Test
    void updateDeliveryStatus_driverCannotUpdateOthersDelivery_throws() {
        User otherDriver = User.builder().id(UUID.randomUUID()).role(RoleType.DRIVER).build();
        order.setDeliveryAssignedTo(otherDriver);
        order.setDeliveryStatus(DeliveryStatus.ASSIGNED);
        DeliveryStatusUpdateRequest request = new DeliveryStatusUpdateRequest();
        request.setStatus(DeliveryStatus.PICKED_UP);

        when(orderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.of(order));
        when(securityService.isAdmin()).thenReturn(false);
        when(securityService.getCurrentUserId()).thenReturn(driverUserId);

        assertThatThrownBy(() -> deliveryService.updateDeliveryStatus(orderId, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You can only update deliveries assigned to you");
    }

    @Test
    void updateDeliveryStatus_delivered_approvesCommission() {
        order.setDeliveryStatus(DeliveryStatus.IN_TRANSIT);
        DeliveryStatusUpdateRequest request = new DeliveryStatusUpdateRequest();
        request.setStatus(DeliveryStatus.DELIVERED);

        when(orderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.of(order));
        when(securityService.isAdmin()).thenReturn(true);
        when(deliveryStateTransition.transition(order, DeliveryStatus.DELIVERED, null, true))
                .thenReturn(new DeliveryStateTransition.TransitionResult(true, false, DeliveryStatus.IN_TRANSIT));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        deliveryService.updateDeliveryStatus(orderId, request);

        verify(commissionLifecycleService).approveCommissionForOrder(orderId);
    }

    @Test
    void updateDeliveryStatus_permanentlyFailed_returnsStock() {
        order.setDeliveryStatus(DeliveryStatus.FAILED);
        DeliveryStatusUpdateRequest request = new DeliveryStatusUpdateRequest();
        request.setStatus(DeliveryStatus.FAILED_PERMANENT);
        request.setReason("Customer unreachable");

        when(orderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.of(order));
        when(securityService.isAdmin()).thenReturn(true);
        when(deliveryStateTransition.transition(order, DeliveryStatus.FAILED_PERMANENT, "Customer unreachable", true))
                .thenReturn(new DeliveryStateTransition.TransitionResult(false, true, DeliveryStatus.FAILED));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        deliveryService.updateDeliveryStatus(orderId, request);

        verify(inventoryService).recordOrderStock(
                eq(product.getId()), eq(1), eq(StockMovementType.RETURN), anyString());
        verify(commissionLifecycleService).cancelCommissionForOrder(orderId);
    }

    @Test
    void getDeliveries_filterByStatus() {
        when(deliveryRepository.findByDeliveryStatus(DeliveryStatus.PENDING)).thenReturn(List.of(order));

        var results = deliveryService.getDeliveries(DeliveryStatus.PENDING, null);

        assertThat(results).hasSize(1);
        verify(deliveryRepository).findByDeliveryStatus(DeliveryStatus.PENDING);
    }

    @Test
    void getMyDeliveries_success() {
        when(securityService.getCurrentUserId()).thenReturn(driverUserId);
        when(deliveryRepository.findByDeliveryAssignedToId(driverUserId)).thenReturn(List.of(order));

        var results = deliveryService.getMyDeliveries();

        assertThat(results).hasSize(1);
    }

    @Test
    void getDeliveryDetail_success() {
        order.setDeliveryStatus(DeliveryStatus.ASSIGNED);
        when(orderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.of(order));

        var result = deliveryService.getDeliveryDetail(orderId);

        assertThat(result).isNotNull();
        assertThat(result.getOrderId()).isEqualTo(orderId);
    }

    @Test
    void getDeliveryDetail_noDelivery_throws() {
        order.setDeliveryStatus(null);
        when(orderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> deliveryService.getDeliveryDetail(orderId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Delivery");
    }
}
