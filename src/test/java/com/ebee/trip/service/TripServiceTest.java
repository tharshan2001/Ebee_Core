package com.ebee.trip.service;

import com.ebee.audit.service.AuditLogService;
import com.ebee.common.security.SecurityService;
import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.delivery.event.DeliveryStatusChangedEvent;
import com.ebee.delivery.service.DeliveryService;
import com.ebee.delivery.service.DeliveryStateTransition;
import com.ebee.driver.entity.Driver;
import com.ebee.driver.repository.DriverRepository;
import com.ebee.expense.entity.Expense;
import com.ebee.expense.enums.ExpenseCategory;
import com.ebee.expense.enums.ExpenseStatus;
import com.ebee.expense.mapper.ExpenseMapper;
import com.ebee.expense.repository.ExpenseRepository;
import com.ebee.order.entity.Order;
import com.ebee.order.enums.OrderStatus;
import com.ebee.order.repository.OrderRepository;
import com.ebee.trip.dto.AddOrdersToTripRequest;
import com.ebee.trip.dto.CreateTripRequest;
import com.ebee.trip.dto.TripResponse;
import com.ebee.trip.dto.UpdateTripRequest;
import com.ebee.trip.entity.Trip;
import com.ebee.trip.enums.TripStatus;
import com.ebee.trip.mapper.TripMapper;
import com.ebee.trip.repository.TripRepository;
import com.ebee.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
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
class TripServiceTest {

    @Mock private TripRepository tripRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private DriverRepository driverRepository;
    @Mock private TripMapper tripMapper;
    @Mock private ExpenseMapper expenseMapper;
    @Mock private AuditLogService auditLogService;
    @Mock private SecurityService securityService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private DeliveryStateTransition deliveryStateTransition;
    @Mock private DeliveryService deliveryService;

    @InjectMocks private TripService tripService;

    private UUID tripId;
    private UUID driverId;
    private UUID orderId;
    private Driver driver;
    private User driverUser;
    private Order order;
    private Trip trip;
    private TripResponse tripResponse;

    @BeforeEach
    void setUp() {
        tripId = UUID.randomUUID();
        driverId = UUID.randomUUID();
        orderId = UUID.randomUUID();

        driverUser = User.builder()
                .id(driverId)
                .firstName("John")
                .lastName("Driver")
                .email("driver@ebee.com")
                .build();

        driver = Driver.builder()
                .id(driverId)
                .user(driverUser)
                .build();

        order = Order.builder()
                .id(orderId)
                .orderNumber("JF-A-20250614-001")
                .status(OrderStatus.SHIPPED)
                .totalAmount(new BigDecimal("5000.00"))
                .paidAmount(new BigDecimal("0.00"))
                .outstandingBalance(new BigDecimal("5000.00"))
                .deliveryStatus(DeliveryStatus.PENDING)
                .build();

        trip = Trip.builder()
                .id(tripId)
                .name("Jaffna Morning Route")
                .startDate(LocalDate.of(2026, 7, 29))
                .endDate(LocalDate.of(2026, 7, 30))
                .status(TripStatus.PLANNED)
                .driver(driver)
                .mileageKm(BigDecimal.ZERO)
                .orders(new ArrayList<>())
                .expenses(new ArrayList<>())
                .build();

        tripResponse = new TripResponse();
        tripResponse.setId(tripId);
        tripResponse.setName("Jaffna Morning Route");
        tripResponse.setStatus(TripStatus.PLANNED);

        ReflectionTestUtils.setField(tripService, "tripOrderLifecycle",
                new TripOrderLifecycle(orderRepository));
    }

    // ----- createTrip -----

    @Test
    void createTrip_success() {
        CreateTripRequest request = new CreateTripRequest();
        request.setName("Jaffna Morning Route");
        request.setStartDate(LocalDate.of(2026, 7, 29));
        request.setEndDate(LocalDate.of(2026, 7, 30));
        request.setDriverId(driverId);

        when(tripRepository.existsByName("Jaffna Morning Route")).thenReturn(false);
        when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(tripRepository.save(any(Trip.class))).thenReturn(trip);
        when(tripMapper.toResponse(trip)).thenReturn(tripResponse);
        when(orderRepository.findByTripId(tripId)).thenReturn(List.of());

        var result = tripService.createTrip(request);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Jaffna Morning Route");
        verify(tripRepository).save(any(Trip.class));
        verify(auditLogService).record(any(), eq("Trip"), any(), anyString());
    }

    @Test
    void createTrip_duplicateName_throws() {
        CreateTripRequest request = new CreateTripRequest();
        request.setName("Jaffna Morning Route");
        request.setStartDate(LocalDate.of(2026, 7, 29));
        request.setEndDate(LocalDate.of(2026, 7, 30));

        when(tripRepository.existsByName("Jaffna Morning Route")).thenReturn(true);

        assertThatThrownBy(() -> tripService.createTrip(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
    }

    // ----- updateTrip -----

    @Test
    void updateTrip_datesLocked_whenInProgress() {
        trip.setStatus(TripStatus.IN_PROGRESS);
        UpdateTripRequest request = new UpdateTripRequest();
        request.setStartDate(LocalDate.of(2026, 8, 1));
        request.setEndDate(LocalDate.of(2026, 8, 5));

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.updateTrip(tripId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot change start date");
    }

    @Test
    void updateTrip_driverLocked_whenInProgress() {
        trip.setStatus(TripStatus.IN_PROGRESS);
        UUID newDriverId = UUID.randomUUID();
        UpdateTripRequest request = new UpdateTripRequest();
        request.setDriverId(newDriverId);

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.updateTrip(tripId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot change driver");
    }

    @Test
    void updateTrip_datesEditable_whenPlanned() {
        UpdateTripRequest request = new UpdateTripRequest();
        request.setStartDate(LocalDate.of(2026, 8, 1));
        request.setEndDate(LocalDate.of(2026, 8, 5));

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripRepository.save(any(Trip.class))).thenReturn(trip);
        when(tripMapper.toResponse(trip)).thenReturn(tripResponse);
        when(orderRepository.findByTripId(tripId)).thenReturn(List.of());

        var result = tripService.updateTrip(tripId, request);

        assertThat(result).isNotNull();
        verify(tripRepository).save(trip);
    }

    @Test
    void updateTrip_syncsOrdersDriver_whenDriverChanges() {
        trip.setOrders(new ArrayList<>(List.of(order)));

        UUID newDriverId = UUID.randomUUID();
        User newDriverUser = User.builder().id(newDriverId).firstName("New").lastName("Driver").build();
        Driver newDriver = Driver.builder().id(newDriverId).user(newDriverUser).build();

        UpdateTripRequest request = new UpdateTripRequest();
        request.setDriverId(newDriverId);

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(driverRepository.findById(newDriverId)).thenReturn(Optional.of(newDriver));
        when(tripRepository.save(any(Trip.class))).thenReturn(trip);
        when(tripMapper.toResponse(trip)).thenReturn(tripResponse);
        when(orderRepository.findByTripId(tripId)).thenReturn(List.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        tripService.updateTrip(tripId, request);

        verify(orderRepository).save(order);
    }

    // ----- startTrip -----

    @Test
    void startTrip_success() {
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripRepository.findByDriverIdAndStatus(driverId, TripStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());
        when(tripRepository.save(any(Trip.class))).thenReturn(trip);
        when(tripMapper.toResponse(trip)).thenReturn(tripResponse);
        when(orderRepository.findByTripId(tripId)).thenReturn(List.of());

        var result = tripService.startTrip(tripId);

        assertThat(result).isNotNull();
        verify(tripRepository).save(trip);
        verify(deliveryService).recalculateDriverAvailability(driverId);
        assertThat(trip.getStatus()).isEqualTo(TripStatus.IN_PROGRESS);
    }

    @Test
    void startTrip_noDriver_throws() {
        trip.setDriver(null);
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.startTrip(tripId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("without a driver");
    }

    @Test
    void startTrip_driverHasOtherInProgress_throws() {
        Trip existingTrip = Trip.builder()
                .id(UUID.randomUUID())
                .name("Existing Trip")
                .status(TripStatus.IN_PROGRESS)
                .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripRepository.findByDriverIdAndStatus(driverId, TripStatus.IN_PROGRESS))
                .thenReturn(Optional.of(existingTrip));

        assertThatThrownBy(() -> tripService.startTrip(tripId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already has an active trip");
    }

    @Test
    void startTrip_notPlanned_throws() {
        trip.setStatus(TripStatus.COMPLETED);
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.startTrip(tripId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only PLANNING trips can be started");
    }

    // ----- addOrdersToTrip -----

    @Test
    void addOrdersToTrip_syncsDeliveryAssignedTo() {
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(tripRepository.save(any(Trip.class))).thenReturn(trip);
        when(tripMapper.toResponse(trip)).thenReturn(tripResponse);
        when(orderRepository.findByTripId(tripId)).thenReturn(List.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        AddOrdersToTripRequest request = new AddOrdersToTripRequest();
        request.setOrderIdsToAdd(List.of(orderId));

        tripService.addOrdersToTrip(tripId, request);

        verify(orderRepository).save(order);
        assertThat(order.getDeliveryAssignedTo()).isEqualTo(driverUser);
        assertThat(order.getTrip()).isEqualTo(trip);
    }

    @Test
    void addOrdersToTrip_orderAlreadyOnTrip_skips() {
        order.setTrip(trip);
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(tripRepository.save(any(Trip.class))).thenReturn(trip);
        when(tripMapper.toResponse(trip)).thenReturn(tripResponse);
        when(orderRepository.findByTripId(tripId)).thenReturn(List.of(order));

        AddOrdersToTripRequest request = new AddOrdersToTripRequest();
        request.setOrderIdsToAdd(List.of(orderId));

        tripService.addOrdersToTrip(tripId, request);

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void addOrdersToTrip_orderAssignedToDifferentDriver_throws() {
        User differentDriverUser = User.builder().id(UUID.randomUUID()).build();
        order.setDeliveryAssignedTo(differentDriverUser);

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        AddOrdersToTripRequest request = new AddOrdersToTripRequest();
        request.setOrderIdsToAdd(List.of(orderId));

        assertThatThrownBy(() -> tripService.addOrdersToTrip(tripId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("assigned to a different driver");
    }

    @Test
    void addOrdersToTrip_orderNotShipped_throws() {
        order.setStatus(OrderStatus.CONFIRMED);

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        AddOrdersToTripRequest request = new AddOrdersToTripRequest();
        request.setOrderIdsToAdd(List.of(orderId));

        assertThatThrownBy(() -> tripService.addOrdersToTrip(tripId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be in SHIPPED status");
    }

    @Test
    void addOrdersToTrip_completedTrip_throws() {
        trip.setStatus(TripStatus.COMPLETED);
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        AddOrdersToTripRequest request = new AddOrdersToTripRequest();
        request.setOrderIdsToAdd(List.of(orderId));

        assertThatThrownBy(() -> tripService.addOrdersToTrip(tripId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot modify orders on a COMPLETED trip");
    }

    // ----- removeOrdersFromTrip -----

    @Test
    void removeOrdersFromTrip_fullyDetachesUnresolvedOrder() {
        order.setTrip(trip);
        order.setDeliveryAssignedTo(driverUser);
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(tripRepository.save(any(Trip.class))).thenReturn(trip);
        when(tripMapper.toResponse(trip)).thenReturn(tripResponse);
        when(orderRepository.findByTripId(tripId)).thenReturn(List.of());
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AddOrdersToTripRequest request = new AddOrdersToTripRequest();
        request.setOrderIdsToRemove(List.of(orderId));

        tripService.addOrdersToTrip(tripId, request);

        assertThat(order.getTrip()).isNull();
        assertThat(order.getDeliveryAssignedTo()).isNull();
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
        verify(orderRepository).save(order);
        verify(eventPublisher).publishEvent(any(DeliveryStatusChangedEvent.class));
    }

    @Test
    void removeOrdersFromTrip_deliveredKeepsDelivery() {
        order.setTrip(trip);
        order.setDeliveryAssignedTo(driverUser);
        order.setDeliveryStatus(DeliveryStatus.DELIVERED);
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(tripRepository.save(any(Trip.class))).thenReturn(trip);
        when(tripMapper.toResponse(trip)).thenReturn(tripResponse);
        when(orderRepository.findByTripId(tripId)).thenReturn(List.of());
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AddOrdersToTripRequest request = new AddOrdersToTripRequest();
        request.setOrderIdsToRemove(List.of(orderId));

        tripService.addOrdersToTrip(tripId, request);

        assertThat(order.getTrip()).isNull();
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(order.getDeliveryAssignedTo()).isEqualTo(driverUser);
        verify(eventPublisher, never()).publishEvent(any(DeliveryStatusChangedEvent.class));
    }

    // ----- cancelTrip -----

    @Test
    void cancelTrip_orphanOrdersBackToPending() {
        order.setTrip(trip);
        order.setDeliveryStatus(DeliveryStatus.ASSIGNED);
        trip.setOrders(new ArrayList<>(List.of(order)));

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripRepository.save(any(Trip.class))).thenReturn(trip);
        when(tripMapper.toResponse(trip)).thenReturn(tripResponse);
        when(orderRepository.findByTripId(tripId)).thenReturn(List.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        var result = tripService.cancelTrip(tripId);

        assertThat(trip.getStatus()).isEqualTo(TripStatus.CANCELLED);
        verify(orderRepository).save(order);
        assertThat(order.getTrip()).isNull();
        assertThat(order.getDeliveryAssignedTo()).isNull();
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
        verify(deliveryService).recalculateDriverAvailability(driverId);
    }

    @Test
    void cancelTrip_deliveredOrdersKeepTrip() {
        order.setTrip(trip);
        order.setDeliveryStatus(DeliveryStatus.DELIVERED);
        Order failedOrder = Order.builder()
                .id(UUID.randomUUID())
                .status(OrderStatus.SHIPPED)
                .deliveryStatus(DeliveryStatus.IN_TRANSIT)
                .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripRepository.save(any(Trip.class))).thenReturn(trip);
        when(tripMapper.toResponse(trip)).thenReturn(tripResponse);
        when(orderRepository.findByTripId(tripId)).thenReturn(List.of(order, failedOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        tripService.cancelTrip(tripId);

        assertThat(order.getTrip()).isEqualTo(trip);
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(failedOrder.getTrip()).isNull();
        assertThat(failedOrder.getDeliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
    }

    @Test
    void cancelTrip_alreadyCancelled_throws() {
        trip.setStatus(TripStatus.CANCELLED);
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.cancelTrip(tripId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Trip is already cancelled");
    }

    @Test
    void cancelTrip_completed_throws() {
        trip.setStatus(TripStatus.COMPLETED);
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.cancelTrip(tripId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot cancel a COMPLETED trip");
    }

    // ----- completeTrip -----

    @Test
    void completeTrip_success() {
        trip.setStatus(TripStatus.IN_PROGRESS);
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripRepository.save(any(Trip.class))).thenReturn(trip);
        when(tripMapper.toResponse(trip)).thenReturn(tripResponse);
        when(orderRepository.findByTripId(tripId)).thenReturn(List.of());

        var result = tripService.completeTrip(tripId);

        assertThat(trip.getStatus()).isEqualTo(TripStatus.COMPLETED);
    }

    @Test
    void completeTrip_notInProgress_throws() {
        trip.setStatus(TripStatus.PLANNED);
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.completeTrip(tripId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only IN_PROGRESS trips can be completed");
    }

    // ----- deleteTrip -----

    @Test
    void deleteTrip_withOrders_throws() {
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(orderRepository.countByTripId(tripId)).thenReturn(1L);

        assertThatThrownBy(() -> tripService.deleteTrip(tripId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot delete a trip that has orders assigned");
    }

    @Test
    void deleteTrip_withExpenses_throws() {
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(orderRepository.countByTripId(tripId)).thenReturn(0L);
        when(expenseRepository.countByTripId(tripId)).thenReturn(2L);

        assertThatThrownBy(() -> tripService.deleteTrip(tripId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot delete a trip that has expenses");
    }

    @Test
    void deleteTrip_success() {
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(orderRepository.countByTripId(tripId)).thenReturn(0L);
        when(expenseRepository.countByTripId(tripId)).thenReturn(0L);

        tripService.deleteTrip(tripId);

        verify(tripRepository).delete(trip);
    }

    @Test
    void deleteTrip_notPlanned_throws() {
        trip.setStatus(TripStatus.IN_PROGRESS);
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.deleteTrip(tripId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only PLANNING trips can be deleted");
    }

    // ----- updateMileage -----

    @Test
    void updateMileage_success() {
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripRepository.save(any(Trip.class))).thenReturn(trip);
        when(tripMapper.toResponse(trip)).thenReturn(tripResponse);
        when(orderRepository.findByTripId(tripId)).thenReturn(List.of());

        var result = tripService.updateMileage(tripId, new BigDecimal("150.5"));

        assertThat(trip.getMileageKm()).isEqualByComparingTo(new BigDecimal("150.5"));
    }

    @Test
    void updateMileage_negative_throws() {
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.updateMileage(tripId, new BigDecimal("-10")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Mileage cannot be negative");
    }

    // ----- date validation -----

    @Test
    void createTrip_endDateBeforeStart_throws() {
        CreateTripRequest request = new CreateTripRequest();
        request.setName("Jaffna Morning Route");
        request.setStartDate(LocalDate.of(2026, 7, 30));
        request.setEndDate(LocalDate.of(2026, 7, 29));
        request.setDriverId(driverId);

        assertThatThrownBy(() -> tripService.createTrip(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("End date cannot be before start date");
    }

    @Test
    void updateTrip_endDateBeforeStart_throws() {
        UpdateTripRequest request = new UpdateTripRequest();
        request.setStartDate(LocalDate.of(2026, 8, 5));
        request.setEndDate(LocalDate.of(2026, 8, 1));

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.updateTrip(tripId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("End date cannot be before start date");
    }

    // ----- F8: cross-trip add -----

    @Test
    void addOrdersToTrip_orderOnAnotherTrip_throws() {
        Trip otherTrip = Trip.builder()
                .id(UUID.randomUUID())
                .name("Other Trip")
                .build();
        order.setTrip(otherTrip);

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        AddOrdersToTripRequest request = new AddOrdersToTripRequest();
        request.setOrderIdsToAdd(List.of(orderId));

        assertThatThrownBy(() -> tripService.addOrdersToTrip(tripId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already on trip");
    }

    // ----- F5: driver PII scoping -----

    @Test
    void getTripById_adminCanViewAnyTrip() {
        when(securityService.isAdmin()).thenReturn(true);
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(orderRepository.findByTripId(tripId)).thenReturn(List.of());
        when(expenseRepository.findByTripIdWithRelations(tripId)).thenReturn(List.of());
        when(tripMapper.toResponse(trip)).thenReturn(tripResponse);

        var result = tripService.getTripById(tripId);

        assertThat(result).isNotNull();
    }

    @Test
    void getTripById_driverOwned_passes() {
        when(securityService.isAdmin()).thenReturn(false);
        when(securityService.getCurrentUserId()).thenReturn(driverId);
        when(driverRepository.findByUserId(driverId)).thenReturn(Optional.of(driver));
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(orderRepository.findByTripId(tripId)).thenReturn(List.of());
        when(expenseRepository.findByTripIdWithRelations(tripId)).thenReturn(List.of());
        when(tripMapper.toResponse(trip)).thenReturn(tripResponse);

        var result = tripService.getTripById(tripId);

        assertThat(result).isNotNull();
    }

    @Test
    void getTripById_driverNotOwned_throws() {
        UUID otherUserId = UUID.randomUUID();
        User otherUser = User.builder().id(otherUserId).build();
        Driver otherDriver = Driver.builder().id(UUID.randomUUID()).user(otherUser).build();

        when(securityService.isAdmin()).thenReturn(false);
        when(securityService.getCurrentUserId()).thenReturn(otherUserId);
        when(driverRepository.findByUserId(otherUserId)).thenReturn(Optional.of(otherDriver));
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.getTripById(tripId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getTripById_driverWithNoDriverRecord_throws() {
        when(securityService.isAdmin()).thenReturn(false);
        when(securityService.getCurrentUserId()).thenReturn(UUID.randomUUID());
        when(driverRepository.findByUserId(any(UUID.class))).thenReturn(Optional.empty());
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.getTripById(tripId))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ----- F4: settlement excludes cancelled / FAILED_PERMANENT, only APPROVED expenses -----

    @Test
    void toTripResponse_excludesNonSettledOrdersAndNonApprovedExpenses() {
        Order saleOrder = order;
        saleOrder.setDeliveryStatus(DeliveryStatus.DELIVERED);

        Order cancelledOrder = Order.builder()
                .id(UUID.randomUUID())
                .status(OrderStatus.CANCELLED)
                .totalAmount(new BigDecimal("3000.00"))
                .paidAmount(new BigDecimal("0.00"))
                .outstandingBalance(new BigDecimal("3000.00"))
                .deliveryStatus(DeliveryStatus.IN_TRANSIT)
                .build();

        Order failedPermanentOrder = Order.builder()
                .id(UUID.randomUUID())
                .status(OrderStatus.SHIPPED)
                .totalAmount(new BigDecimal("2000.00"))
                .paidAmount(new BigDecimal("0.00"))
                .outstandingBalance(new BigDecimal("2000.00"))
                .deliveryStatus(DeliveryStatus.FAILED_PERMANENT)
                .build();

        Expense approved = Expense.builder()
                .id(UUID.randomUUID())
                .amount(new BigDecimal("100.00"))
                .status(ExpenseStatus.APPROVED)
                .category(ExpenseCategory.PETROL)
                .build();
        Expense pending = Expense.builder()
                .id(UUID.randomUUID())
                .amount(new BigDecimal("50.00"))
                .status(ExpenseStatus.PENDING)
                .category(ExpenseCategory.PETROL)
                .build();

        when(securityService.isAdmin()).thenReturn(true);
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(orderRepository.findByTripId(tripId)).thenReturn(List.of(saleOrder, cancelledOrder, failedPermanentOrder));
        when(expenseRepository.findByTripIdWithRelations(tripId)).thenReturn(List.of(approved, pending));
        when(tripMapper.toResponse(trip)).thenReturn(tripResponse);

        tripService.getTripById(tripId);

        assertThat(tripResponse.getExpectedAmount()).isEqualByComparingTo(new BigDecimal("5000.00"));
        assertThat(tripResponse.getCollectedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(tripResponse.getOutstandingAmount()).isEqualByComparingTo(new BigDecimal("5000.00"));
        assertThat(tripResponse.getUnpaidOrderCount()).isEqualTo(1);
        assertThat(tripResponse.getPaidOrderCount()).isZero();
        assertThat(tripResponse.getPartialOrderCount()).isZero();
        assertThat(tripResponse.getTotalExpenses()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(tripResponse.getNetAmount()).isEqualByComparingTo(new BigDecimal("-100.00"));
    }

    @Test
    void toTripResponse_paidOrdersCountTowardsCollected() {
        order.setDeliveryStatus(DeliveryStatus.DELIVERED);
        order.setPaidAmount(new BigDecimal("5000.00"));
        order.setOutstandingBalance(BigDecimal.ZERO);

        when(securityService.isAdmin()).thenReturn(true);
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(orderRepository.findByTripId(tripId)).thenReturn(List.of(order));
        when(expenseRepository.findByTripIdWithRelations(tripId)).thenReturn(List.of());
        when(tripMapper.toResponse(trip)).thenReturn(tripResponse);

        tripService.getTripById(tripId);

        assertThat(tripResponse.getCollectedAmount()).isEqualByComparingTo(new BigDecimal("5000.00"));
        assertThat(tripResponse.getOutstandingAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ----- completeTrip blocks non-terminal orders -----

    @Test
    void completeTrip_nonTerminalOrder_blocksCompletion() {
        trip.setStatus(TripStatus.IN_PROGRESS);
        order.setDeliveryStatus(DeliveryStatus.FAILED);
        order.setDeliveryFailureReason("Customer not available");

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(orderRepository.findByTripId(tripId)).thenReturn(List.of(order));

        assertThatThrownBy(() -> tripService.completeTrip(tripId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot complete trip")
                .hasMessageContaining("JF-A-20250614-001")
                .hasMessageContaining("DELIVERED or FAILED_PERMANENT");

        assertThat(trip.getStatus()).isEqualTo(TripStatus.IN_PROGRESS);
        assertThat(order.getTrip()).isNull();
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(order.getDeliveryFailureReason()).isEqualTo("Customer not available");
        verify(tripRepository, never()).save(any(Trip.class));
    }

    @Test
    void completeTrip_anyNonTerminalDeliveryStatus_blocksCompletion() {
        for (DeliveryStatus status : List.of(
                DeliveryStatus.PENDING, DeliveryStatus.ASSIGNED,
                DeliveryStatus.PICKED_UP, DeliveryStatus.IN_TRANSIT, DeliveryStatus.FAILED)) {
            trip.setStatus(TripStatus.IN_PROGRESS);
            order.setDeliveryStatus(status);

            when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
            when(orderRepository.findByTripId(tripId)).thenReturn(List.of(order));

            assertThatThrownBy(() -> tripService.completeTrip(tripId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot complete trip");

            assertThat(trip.getStatus()).isEqualTo(TripStatus.IN_PROGRESS);
        }
    }

    @Test
    void completeTrip_terminalOrders_doNotBlock() {
        List<Order> terminalOrders = List.of(
                Order.builder().id(UUID.randomUUID()).orderNumber("ORD-1")
                        .status(OrderStatus.SHIPPED).deliveryStatus(DeliveryStatus.DELIVERED).build(),
                Order.builder().id(UUID.randomUUID()).orderNumber("ORD-2")
                        .status(OrderStatus.SHIPPED).deliveryStatus(DeliveryStatus.FAILED_PERMANENT).build(),
                Order.builder().id(UUID.randomUUID()).orderNumber("ORD-3")
                        .status(OrderStatus.CANCELLED).deliveryStatus(DeliveryStatus.ASSIGNED).build(),
                Order.builder().id(UUID.randomUUID()).orderNumber("ORD-4")
                        .status(OrderStatus.RETURNED).deliveryStatus(null).build());

        trip.setStatus(TripStatus.IN_PROGRESS);
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripRepository.save(any(Trip.class))).thenReturn(trip);
        when(tripMapper.toResponse(trip)).thenReturn(tripResponse);
        when(orderRepository.findByTripId(tripId)).thenReturn(terminalOrders);

        var result = tripService.completeTrip(tripId);

        assertThat(result).isEqualTo(tripResponse);
        assertThat(trip.getStatus()).isEqualTo(TripStatus.COMPLETED);
    }

    @Test
    void completeTrip_deliveredOrderKeepsTrip() {
        trip.setStatus(TripStatus.IN_PROGRESS);
        order.setTrip(trip);
        order.setDeliveryStatus(DeliveryStatus.DELIVERED);

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripRepository.save(any(Trip.class))).thenReturn(trip);
        when(tripMapper.toResponse(trip)).thenReturn(tripResponse);
        when(orderRepository.findByTripId(tripId)).thenReturn(List.of(order));

        tripService.completeTrip(tripId);

        assertThat(order.getTrip()).isEqualTo(trip);
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
    }
}
