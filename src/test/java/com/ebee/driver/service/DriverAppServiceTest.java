package com.ebee.driver.service;

import com.ebee.alert.service.AlertService;
import com.ebee.audit.service.AuditLogService;
import com.ebee.commission.service.CommissionLifecycleService;
import com.ebee.common.port.StoragePort;
import com.ebee.common.security.SecurityService;
import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.delivery.event.DeliveryStatusChangedEvent;
import com.ebee.delivery.repository.DeliveryRepository;
import com.ebee.delivery.service.DeliveryService;
import com.ebee.delivery.service.DeliveryStateTransition;
import com.ebee.driver.entity.Driver;
import com.ebee.driver.enums.DriverAvailability;
import com.ebee.driver.repository.DriverRepository;
import com.ebee.expense.mapper.ExpenseMapper;
import com.ebee.expense.repository.ExpenseRepository;
import com.ebee.notification.NotificationPort;
import com.ebee.order.entity.Order;
import com.ebee.order.enums.OrderStatus;
import com.ebee.order.repository.OrderRepository;
import com.ebee.trip.entity.Trip;
import com.ebee.trip.enums.TripStatus;
import com.ebee.trip.event.TripStatusChangedEvent;
import com.ebee.trip.mapper.TripMapper;
import com.ebee.trip.repository.TripRepository;
import com.ebee.trip.service.TripOrderLifecycle;
import com.ebee.user.entity.User;
import com.ebee.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverAppServiceTest {

    @Mock private DriverRepository driverRepository;
    @Mock private DriverService driverService;
    @Mock private UserRepository userRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private DeliveryRepository deliveryRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private ExpenseMapper expenseMapper;
    @Mock private DeliveryService deliveryService;
    @Mock private DeliveryStateTransition deliveryStateTransition;
    @Mock private CommissionLifecycleService commissionLifecycleService;
    @Mock private AlertService alertService;
    @Mock private SecurityService securityService;
    @Mock private AuditLogService auditLogService;
    @Mock private StoragePort storagePort;
    @Mock private NotificationPort notificationPort;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private TripRepository tripRepository;
    @Mock private TripMapper tripMapper;

    @InjectMocks private DriverAppService driverAppService;
    @InjectMocks private DriverTripService driverTripService;
    @InjectMocks private DriverContext driverContext;

    @org.junit.jupiter.api.BeforeEach
    void wireDriverContext() {
        // Trips live in DriverTripService; the signed-in driver comes from a real DriverContext on the same mocks.
        ReflectionTestUtils.setField(driverTripService, "driverContext", driverContext);
    }

    private UUID userId;
    private UUID driverId;
    private UUID tripId;
    private UUID orderId;
    private User user;
    private Driver driver;
    private Trip trip;
    private Order order;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        driverId = UUID.randomUUID();
        tripId = UUID.randomUUID();
        orderId = UUID.randomUUID();

        user = User.builder()
                .id(userId)
                .firstName("John")
                .lastName("Driver")
                .email("driver@ebee.com")
                .build();

        driver = Driver.builder()
                .id(driverId)
                .user(user)
                .availability(DriverAvailability.ON_TRIP)
                .build();

        trip = Trip.builder()
                .id(tripId)
                .name("Jaffna Morning Route")
                .startDate(LocalDate.of(2026, 7, 29))
                .endDate(LocalDate.of(2026, 7, 30))
                .status(TripStatus.IN_PROGRESS)
                .driver(driver)
                .mileageKm(BigDecimal.ZERO)
                .orders(new ArrayList<>())
                .expenses(new ArrayList<>())
                .build();

        order = Order.builder()
                .id(orderId)
                .orderNumber("JF-A-20250614-001")
                .status(OrderStatus.SHIPPED)
                .totalAmount(new BigDecimal("5000.00"))
                .deliveryStatus(DeliveryStatus.ASSIGNED)
                .build();

        when(securityService.getCurrentUserId()).thenReturn(userId);
        when(driverRepository.findByUserId(userId)).thenReturn(Optional.of(driver));

        ReflectionTestUtils.setField(driverTripService, "tripOrderLifecycle",
                new TripOrderLifecycle(orderRepository));
    }

    @Test
    void completeTrip_nonTerminalOrder_blocksCompletion() {
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(orderRepository.findByTripId(tripId)).thenReturn(List.of(order));

        assertThatThrownBy(() -> driverTripService.completeTrip(tripId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot complete trip")
                .hasMessageContaining("DELIVERED, FAILED_PERMANENT, or FAILED");

        assertThat(order.getTrip()).isNull();
        assertThat(order.getDeliveryAssignedTo()).isNull();
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
        assertThat(trip.getStatus()).isEqualTo(TripStatus.IN_PROGRESS);
        verify(deliveryService, never()).recalculateDriverAvailability(userId);
        verify(eventPublisher, never()).publishEvent(any(DeliveryStatusChangedEvent.class));
        verify(eventPublisher, never()).publishEvent(any(TripStatusChangedEvent.class));
    }

    @Test
    void completeTrip_deliveredOrdersKeepTrip() {
        order.setTrip(trip);
        order.setDeliveryStatus(DeliveryStatus.DELIVERED);

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripRepository.save(any(Trip.class))).thenReturn(trip);
        when(orderRepository.findByTripId(tripId)).thenReturn(List.of(order));

        driverTripService.completeTrip(tripId);

        assertThat(order.getTrip()).isEqualTo(trip);
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        verify(eventPublisher, never()).publishEvent(any(DeliveryStatusChangedEvent.class));
    }

    @Test
    void completeTrip_failedOrder_releasesAndResets() {
        order.setTrip(trip);
        order.setDeliveryAssignedTo(user);
        order.setDeliveryStatus(DeliveryStatus.FAILED);
        order.setDeliveryFailureReason("Customer not available");

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripRepository.save(any(Trip.class))).thenReturn(trip);
        when(orderRepository.findByTripId(tripId)).thenReturn(List.of(order));

        driverTripService.completeTrip(tripId);

        assertThat(trip.getStatus()).isEqualTo(TripStatus.COMPLETED);
        assertThat(order.getTrip()).isNull();
        assertThat(order.getDeliveryAssignedTo()).isNull();
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(order.getDeliveryFailureReason()).isNull();
        verify(orderRepository).save(order);
        verify(eventPublisher).publishEvent(any(DeliveryStatusChangedEvent.class));
    }

    @Test
    void updateMileage_negative_throws() {
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> driverTripService.updateMileage(tripId, new BigDecimal("-10.0")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Mileage cannot be negative");

        assertThat(trip.getMileageKm()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(tripRepository, never()).save(any(Trip.class));
    }

    @Test
    void startTrip_assignsPendingOrders() {
        trip.setStatus(TripStatus.PLANNED);
        order.setDeliveryStatus(DeliveryStatus.PENDING);
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripRepository.findByDriverIdAndStatus(driverId, TripStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());
        when(tripRepository.save(any(Trip.class))).thenReturn(trip);
        when(orderRepository.findByTripId(tripId)).thenReturn(List.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(deliveryStateTransition.transition(order, DeliveryStatus.ASSIGNED, "Trip started", false))
                .thenReturn(new DeliveryStateTransition.TransitionResult(false, false, DeliveryStatus.PENDING));

        driverTripService.startTrip(tripId);

        assertThat(trip.getStatus()).isEqualTo(TripStatus.IN_PROGRESS);
        verify(deliveryStateTransition).transition(order, DeliveryStatus.ASSIGNED, "Trip started", false);
        verify(deliveryService).recalculateDriverAvailability(userId);
        verify(eventPublisher).publishEvent(any(DeliveryStatusChangedEvent.class));
    }

    @Test
    void removeOrdersFromTrip_fullyDetachesUnresolvedOrder() {
        order.setTrip(trip);
        order.setDeliveryAssignedTo(user);
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(tripRepository.save(any(Trip.class))).thenReturn(trip);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        com.ebee.trip.dto.AddOrdersToTripRequest request = new com.ebee.trip.dto.AddOrdersToTripRequest();
        request.setOrderIdsToRemove(List.of(orderId));

        driverTripService.addOrdersToTrip(tripId, request);

        assertThat(order.getTrip()).isNull();
        assertThat(order.getDeliveryAssignedTo()).isNull();
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
        verify(eventPublisher).publishEvent(any(DeliveryStatusChangedEvent.class));
    }

    @Test
    void removeOrdersFromTrip_deliveredKeepsDelivery() {
        order.setTrip(trip);
        order.setDeliveryAssignedTo(user);
        order.setDeliveryStatus(DeliveryStatus.DELIVERED);
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(tripRepository.save(any(Trip.class))).thenReturn(trip);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        com.ebee.trip.dto.AddOrdersToTripRequest request = new com.ebee.trip.dto.AddOrdersToTripRequest();
        request.setOrderIdsToRemove(List.of(orderId));

        driverTripService.addOrdersToTrip(tripId, request);

        assertThat(order.getTrip()).isNull();
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(order.getDeliveryAssignedTo()).isEqualTo(user);
        verify(eventPublisher, never()).publishEvent(any(DeliveryStatusChangedEvent.class));
    }
}
