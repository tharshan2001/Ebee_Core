package com.ebee.trip.service;

import com.ebee.audit.enums.AuditAction;
import com.ebee.audit.service.AuditLogService;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.response.PageResponse;
import com.ebee.common.security.SecurityService;
import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.delivery.event.DeliveryStatusChangedEvent;
import com.ebee.delivery.service.DeliveryService;
import com.ebee.delivery.service.DeliveryStateTransition;
import com.ebee.driver.entity.Driver;
import com.ebee.driver.repository.DriverRepository;
import com.ebee.expense.dto.ExpenseResponse;
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
import com.ebee.trip.dto.TripOrderMarkerDto;
import com.ebee.trip.dto.TripResponse;
import com.ebee.trip.dto.UpdateTripRequest;
import com.ebee.trip.entity.Trip;
import com.ebee.trip.enums.TripStatus;
import com.ebee.trip.event.TripOrdersChangedEvent;
import com.ebee.trip.event.TripStatusChangedEvent;
import com.ebee.trip.mapper.TripMapper;
import com.ebee.trip.repository.TripRepository;
import com.ebee.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripService {

    private final TripRepository tripRepository;
    private final OrderRepository orderRepository;
    private final ExpenseRepository expenseRepository;
    private final DriverRepository driverRepository;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final DeliveryStateTransition deliveryStateTransition;
    private final DeliveryService deliveryService;
    private final TripOrderLifecycle tripOrderLifecycle;
    private final TripViews tripViews;
    private final TripQueryService tripQueryService;

    // ── lookups (TripQueryService) ──

    public TripResponse getTripById(UUID tripId) {
        return tripQueryService.getTripById(tripId);
    }

    public List<TripResponse> getAllTrips(TripStatus status, UUID driverId, java.time.LocalDate dateFrom, java.time.LocalDate dateTo) {
        return tripQueryService.getAllTrips(status, driverId, dateFrom, dateTo);
    }

    public PageResponse<TripResponse> getAllTripsPaginated(TripStatus status, UUID driverId, java.time.LocalDate dateFrom, java.time.LocalDate dateTo, Pageable pageable) {
        return tripQueryService.getAllTripsPaginated(status, driverId, dateFrom, dateTo, pageable);
    }

    public List<TripOrderMarkerDto> getActiveTripOrderMarkers(UUID driverId) {
        return tripQueryService.getActiveTripOrderMarkers(driverId);
    }

    public List<TripOrderMarkerDto> getTripOrderMarkers(UUID tripId) {
        return tripQueryService.getTripOrderMarkers(tripId);
    }

    @Transactional
    public TripResponse createTrip(CreateTripRequest request) {
        if (tripRepository.existsByName(request.getName())) {
            throw new IllegalStateException("Trip with name '" + request.getName() + "' already exists");
        }
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalStateException("End date cannot be before start date");
        }

        Trip.TripBuilder builder = Trip.builder()
                .name(request.getName())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .notes(request.getNotes())
                .status(TripStatus.PLANNED);

        if (request.getDriverId() != null) {
            Driver driver = driverRepository.findById(request.getDriverId())
                    .orElseThrow(() -> new ResourceNotFoundException("Driver", "id", request.getDriverId()));
            builder.driver(driver);
        }

        Trip trip = tripRepository.save(builder.build());

        auditLogService.record(AuditAction.CREATE, "Trip", trip.getId(),
                "Trip '" + trip.getName() + "' created");

        log.info("Trip {} created with name {}", trip.getId(), trip.getName());
        return tripViews.toTripResponse(trip);
    }

    @Transactional
    public TripResponse updateTrip(UUID tripId, UpdateTripRequest request) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", "id", tripId));

        boolean locked = trip.getStatus() != TripStatus.PLANNED;

        if (locked && request.getStartDate() != null) {
            throw new IllegalStateException("Cannot change start date of a trip that is not in PLANNING status");
        }
        if (locked && request.getEndDate() != null) {
            throw new IllegalStateException("Cannot change end date of a trip that is not in PLANNING status");
        }
        if (locked && request.getDriverId() != null) {
            throw new IllegalStateException("Cannot change driver of a trip that is not in PLANNING status");
        }

        if (request.getName() != null && !request.getName().equals(trip.getName())) {
            if (tripRepository.existsByNameAndIdNot(request.getName(), tripId)) {
                throw new IllegalStateException("Trip with name '" + request.getName() + "' already exists");
            }
            trip.setName(request.getName());
        }

        if (request.getStartDate() != null) {
            trip.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            trip.setEndDate(request.getEndDate());
        }
        if (request.getNotes() != null) {
            trip.setNotes(request.getNotes());
        }

        if (trip.getEndDate().isBefore(trip.getStartDate())) {
            throw new IllegalStateException("End date cannot be before start date");
        }

        if (request.getDriverId() != null && trip.getDriver() == null) {
            Driver driver = driverRepository.findById(request.getDriverId())
                    .orElseThrow(() -> new ResourceNotFoundException("Driver", "id", request.getDriverId()));
            trip.setDriver(driver);
            syncOrdersDriver(trip);
        } else if (request.getDriverId() != null && !request.getDriverId().equals(trip.getDriver().getId())) {
            Driver newDriver = driverRepository.findById(request.getDriverId())
                    .orElseThrow(() -> new ResourceNotFoundException("Driver", "id", request.getDriverId()));
            Driver oldDriver = trip.getDriver();
            trip.setDriver(newDriver);
            syncOrdersDriver(trip);
            auditLogService.record(AuditAction.UPDATE, "Trip", trip.getId(),
                    "Driver changed from " + oldDriver.getUser().getFirstName() + " to " + newDriver.getUser().getFirstName());
        }

        trip = tripRepository.save(trip);

        if (request.getMileageKm() != null) {
            if (request.getMileageKm().signum() < 0) {
                throw new IllegalStateException("Mileage cannot be negative");
            }
            trip.setMileageKm(request.getMileageKm());
            trip = tripRepository.save(trip);
        }

        auditLogService.record(AuditAction.UPDATE, "Trip", trip.getId(),
                "Trip '" + trip.getName() + "' updated");

        return tripViews.toTripResponse(trip);
    }

    @Transactional
    public TripResponse cancelTrip(UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", "id", tripId));

        if (trip.getStatus() == TripStatus.CANCELLED) {
            throw new IllegalStateException("Trip is already cancelled");
        }
        if (trip.getStatus() == TripStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel a COMPLETED trip");
        }

        TripStatus previousStatus = trip.getStatus();
        trip.setStatus(TripStatus.CANCELLED);
        trip = tripRepository.save(trip);

        List<Order> orders = tripOrderLifecycle.getTripOrders(trip.getId());
        List<Order> unresolved = orders.stream()
                .filter(tripOrderLifecycle::isUnresolved)
                .toList();

        UUID driverUserId = trip.getDriver() != null ? trip.getDriver().getUser().getId() : null;

        for (Order order : unresolved) {
            DeliveryStatus previousDeliveryStatus = order.getDeliveryStatus();
            tripOrderLifecycle.detachAndReset(order);
            eventPublisher.publishEvent(new DeliveryStatusChangedEvent(
                    this, order, previousDeliveryStatus, "Trip cancelled — order returned to pending", LocalDateTime.now()));
        }

        if (driverUserId != null) {
            deliveryService.recalculateDriverAvailability(driverUserId);
        }

        auditLogService.record(AuditAction.UPDATE, "Trip", trip.getId(),
                "Trip '" + trip.getName() + "' cancelled");

        eventPublisher.publishEvent(new TripStatusChangedEvent(this, trip, previousStatus, orders.size()));
        eventPublisher.publishEvent(new TripOrdersChangedEvent(this, trip, List.of()));

        return tripViews.toTripResponse(trip);
    }

    @Transactional
    public void deleteTrip(UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", "id", tripId));

        if (trip.getStatus() != TripStatus.PLANNED) {
            throw new IllegalStateException("Only PLANNING trips can be deleted");
        }
        if (orderRepository.countByTripId(trip.getId()) > 0) {
            throw new IllegalStateException("Cannot delete a trip that has orders assigned");
        }
        if (expenseRepository.countByTripId(trip.getId()) > 0) {
            throw new IllegalStateException("Cannot delete a trip that has expenses");
        }

        tripRepository.delete(trip);

        auditLogService.record(AuditAction.DELETE, "Trip", tripId,
                "Trip '" + trip.getName() + "' deleted");
    }

    @Transactional
    public TripResponse startTrip(UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", "id", tripId));

        if (trip.getStatus() != TripStatus.PLANNED) {
            throw new IllegalStateException("Only PLANNING trips can be started");
        }
        if (trip.getDriver() == null) {
            throw new IllegalStateException("Cannot start a trip without a driver assigned");
        }

        Optional<Trip> existingInProgress = tripRepository.findByDriverIdAndStatus(
                trip.getDriver().getId(), TripStatus.IN_PROGRESS);
        if (existingInProgress.isPresent()) {
            throw new IllegalStateException("Driver already has an active trip: " + existingInProgress.get().getName());
        }

        TripStatus previousStatus = trip.getStatus();
        trip.setStatus(TripStatus.IN_PROGRESS);
        trip = tripRepository.save(trip);

        auditLogService.record(AuditAction.UPDATE, "Trip", trip.getId(),
                "Trip '" + trip.getName() + "' started");

        List<Order> orders = tripOrderLifecycle.getTripOrders(trip.getId());
        for (Order order : orders) {
            if (tripOrderLifecycle.isPendingForAssignment(order)) {
                DeliveryStateTransition.TransitionResult result =
                        deliveryStateTransition.transition(order, DeliveryStatus.ASSIGNED, "Trip started", true);
                orderRepository.save(order);
                eventPublisher.publishEvent(new DeliveryStatusChangedEvent(
                        this, order, result.previousStatus(), "Trip started", LocalDateTime.now()));
            }
        }

        deliveryService.recalculateDriverAvailability(trip.getDriver().getUser().getId());

        eventPublisher.publishEvent(new TripStatusChangedEvent(this, trip, previousStatus, orders.size()));
        eventPublisher.publishEvent(new TripOrdersChangedEvent(this, trip, tripViews.buildOrderMarkers(trip)));

        return tripViews.toTripResponse(trip);
    }

    @Transactional
    public TripResponse addOrdersToTrip(UUID tripId, AddOrdersToTripRequest request) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", "id", tripId));

        if (trip.getStatus() == TripStatus.COMPLETED || trip.getStatus() == TripStatus.CANCELLED) {
            throw new IllegalStateException("Cannot modify orders on a " + trip.getStatus() + " trip");
        }
        if (trip.getDriver() == null) {
            throw new IllegalStateException("Cannot add orders to a trip without a driver");
        }

        if (request.getOrderIdsToAdd() != null && !request.getOrderIdsToAdd().isEmpty()) {
            for (UUID orderId : request.getOrderIdsToAdd()) {
                Order order = orderRepository.findById(orderId)
                        .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

                if (order.getTrip() != null && order.getTrip().getId().equals(tripId)) {
                    continue;
                }

                if (order.getStatus() != OrderStatus.SHIPPED) {
                    throw new IllegalStateException("Order " + order.getOrderNumber() + " must be in SHIPPED status to add to a trip");
                }

                if (order.getDeliveryAssignedTo() != null
                        && !order.getDeliveryAssignedTo().getId().equals(trip.getDriver().getUser().getId())) {
                    throw new IllegalStateException("Order " + order.getOrderNumber() + " is assigned to a different driver");
                }

                if (order.getTrip() != null) {
                    throw new IllegalStateException("Order " + order.getOrderNumber()
                            + " is already on trip '" + order.getTrip().getName() + "'");
                }

                order.setTrip(trip);
                order.setDeliveryAssignedTo(trip.getDriver().getUser());
                orderRepository.save(order);
            }
        }

        if (request.getOrderIdsToRemove() != null && !request.getOrderIdsToRemove().isEmpty()) {
            for (UUID orderId : request.getOrderIdsToRemove()) {
                Order order = orderRepository.findById(orderId)
                        .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

                if (order.getTrip() == null || !order.getTrip().getId().equals(tripId)) {
                    throw new IllegalStateException("Order " + order.getOrderNumber() + " is not on this trip");
                }

                if (tripOrderLifecycle.isUnresolved(order)) {
                    DeliveryStatus previousDeliveryStatus = order.getDeliveryStatus();
                    tripOrderLifecycle.detachAndReset(order);
                    eventPublisher.publishEvent(new DeliveryStatusChangedEvent(
                            this, order, previousDeliveryStatus, "Order removed from trip", LocalDateTime.now()));
                } else {
                    order.setTrip(null);
                    orderRepository.save(order);
                }
            }
        }

        trip = tripRepository.save(trip);

        auditLogService.record(AuditAction.UPDATE, "Trip", trip.getId(),
                "Orders updated on trip '" + trip.getName() + "'");

        eventPublisher.publishEvent(new TripOrdersChangedEvent(this, trip, tripViews.buildOrderMarkers(trip)));

        return tripViews.toTripResponse(trip);
    }

    @Transactional
    public TripResponse updateMileage(UUID tripId, BigDecimal mileageKm) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", "id", tripId));

        if (mileageKm != null && mileageKm.signum() < 0) {
            throw new IllegalStateException("Mileage cannot be negative");
        }

        trip.setMileageKm(mileageKm);
        trip = tripRepository.save(trip);

        auditLogService.record(AuditAction.UPDATE, "Trip", trip.getId(),
                "Mileage updated to " + mileageKm + " km on trip '" + trip.getName() + "'");

        return tripViews.toTripResponse(trip);
    }

    @Transactional
    public TripResponse completeTrip(UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", "id", tripId));

        if (trip.getStatus() != TripStatus.IN_PROGRESS) {
            throw new IllegalStateException("Only IN_PROGRESS trips can be completed");
        }

        List<Order> orders = tripOrderLifecycle.getTripOrders(trip.getId());
        List<Order> notCompletable = orders.stream()
                .filter(order -> !tripOrderLifecycle.isCompletableForTrip(order))
                .toList();

        if (!notCompletable.isEmpty()) {
            String orderNumbers = notCompletable.stream()
                    .map(Order::getOrderNumber)
                    .collect(Collectors.joining(", "));
            throw new IllegalStateException("Cannot complete trip '" + trip.getName() + "': " + notCompletable.size()
                    + " order(s) not yet delivered (" + orderNumbers
                    + "). All orders must be DELIVERED, FAILED_PERMANENT, or FAILED.");
        }

        UUID driverUserId = trip.getDriver() != null ? trip.getDriver().getUser().getId() : null;

        if (driverUserId != null) {
            deliveryService.recalculateDriverAvailability(driverUserId);
        }

        TripStatus previousStatus = trip.getStatus();
        trip.setStatus(TripStatus.COMPLETED);
        trip = tripRepository.save(trip);

        for (Order order : orders) {
            if (tripOrderLifecycle.isRetryableFailed(order)) {
                DeliveryStatus previousDeliveryStatus = order.getDeliveryStatus();
                tripOrderLifecycle.detachAndReset(order);
                eventPublisher.publishEvent(new DeliveryStatusChangedEvent(
                        this, order, previousDeliveryStatus,
                        "Trip completed — order released for retry", LocalDateTime.now()));
            }
        }

        auditLogService.record(AuditAction.UPDATE, "Trip", trip.getId(),
                "Trip '" + trip.getName() + "' completed");

        eventPublisher.publishEvent(new TripStatusChangedEvent(this, trip, previousStatus, orders.size()));
        eventPublisher.publishEvent(new TripOrdersChangedEvent(this, trip, List.of()));

        return tripViews.toTripResponse(trip);
    }

    private void syncOrdersDriver(Trip trip) {
        for (Order order : tripOrderLifecycle.getTripOrders(trip.getId())) {
            if (trip.getDriver() != null) {
                order.setDeliveryAssignedTo(trip.getDriver().getUser());
            }
            orderRepository.save(order);
        }
    }

}
