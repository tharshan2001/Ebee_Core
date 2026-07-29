package com.ebee.trip.service;

import com.ebee.audit.service.AuditLogService;
import com.ebee.common.security.SecurityService;
import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.driver.entity.Driver;
import com.ebee.driver.repository.DriverRepository;
import com.ebee.expense.entity.Expense;
import com.ebee.expense.enums.ExpenseCategory;
import com.ebee.expense.mapper.ExpenseMapper;
import com.ebee.expense.repository.ExpenseRepository;
import com.ebee.order.entity.Order;
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
        when(expenseRepository.findByTripId(tripId)).thenReturn(List.of());

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
        when(expenseRepository.findByTripId(tripId)).thenReturn(List.of());

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
        when(expenseRepository.findByTripId(tripId)).thenReturn(List.of());
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
        when(expenseRepository.findByTripId(tripId)).thenReturn(List.of());

        var result = tripService.startTrip(tripId);

        assertThat(result).isNotNull();
        verify(tripRepository).save(trip);
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
        when(expenseRepository.findByTripId(tripId)).thenReturn(List.of());
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
        when(expenseRepository.findByTripId(tripId)).thenReturn(List.of());

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
    void removeOrdersFromTrip_clearsTripId() {
        order.setTrip(trip);
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(tripRepository.save(any(Trip.class))).thenReturn(trip);
        when(tripMapper.toResponse(trip)).thenReturn(tripResponse);
        when(orderRepository.findByTripId(tripId)).thenReturn(List.of());
        when(expenseRepository.findByTripId(tripId)).thenReturn(List.of());
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        AddOrdersToTripRequest request = new AddOrdersToTripRequest();
        request.setOrderIdsToRemove(List.of(orderId));

        tripService.addOrdersToTrip(tripId, request);

        assertThat(order.getTrip()).isNull();
        verify(orderRepository).save(order);
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
        when(orderRepository.findByTripId(tripId)).thenReturn(List.of());
        when(expenseRepository.findByTripId(tripId)).thenReturn(List.of());
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        var result = tripService.cancelTrip(tripId);

        assertThat(trip.getStatus()).isEqualTo(TripStatus.CANCELLED);
        verify(orderRepository).save(order);
        assertThat(order.getTrip()).isNull();
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
    }

    // ----- completeTrip -----

    @Test
    void completeTrip_success() {
        trip.setStatus(TripStatus.IN_PROGRESS);
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripRepository.save(any(Trip.class))).thenReturn(trip);
        when(tripMapper.toResponse(trip)).thenReturn(tripResponse);
        when(orderRepository.findByTripId(tripId)).thenReturn(List.of());
        when(expenseRepository.findByTripId(tripId)).thenReturn(List.of());

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
        trip.setOrders(new ArrayList<>(List.of(order)));
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.deleteTrip(tripId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot delete a trip that has orders assigned");
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
        when(expenseRepository.findByTripId(tripId)).thenReturn(List.of());

        var result = tripService.updateMileage(tripId, new BigDecimal("150.5"));

        assertThat(trip.getMileageKm()).isEqualByComparingTo(new BigDecimal("150.5"));
    }
}
