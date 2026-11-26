package com.ebee.driver.service;

import com.ebee.alert.enums.AlertType;
import com.ebee.alert.service.AlertService;
import com.ebee.audit.enums.AuditAction;
import com.ebee.audit.service.AuditLogService;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.commission.service.CommissionLifecycleService;
import com.ebee.common.security.SecurityService;
import com.ebee.common.port.StoragePort;
import com.ebee.common.util.DateUtils;
import com.ebee.common.util.PhoneUtils;
import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.delivery.event.DeliveryStatusChangedEvent;
import com.ebee.delivery.repository.DeliveryRepository;
import com.ebee.delivery.service.DeliveryService;
import com.ebee.delivery.service.DeliveryStateTransition;
import com.ebee.driver.dto.*;
import com.ebee.driver.entity.Driver;
import com.ebee.driver.enums.DriverAvailability;
import com.ebee.driver.repository.DriverRepository;
import com.ebee.expense.dto.ExpenseRequest;
import com.ebee.expense.dto.ExpenseResponse;
import com.ebee.expense.entity.Expense;
import com.ebee.expense.enums.ExpenseStatus;
import com.ebee.expense.mapper.ExpenseMapper;
import com.ebee.expense.repository.ExpenseRepository;
import com.ebee.notification.NotificationPort;
import com.ebee.order.entity.Order;
import com.ebee.order.entity.OrderItem;
import com.ebee.order.enums.OrderStatus;
import com.ebee.order.repository.OrderRepository;
import com.ebee.trip.dto.AddOrdersToTripRequest;
import com.ebee.trip.dto.TripOrderMarkerDto;
import com.ebee.trip.dto.TripResponse;
import com.ebee.trip.entity.Trip;
import com.ebee.trip.enums.TripStatus;
import com.ebee.trip.event.TripOrdersChangedEvent;
import com.ebee.trip.event.TripStatusChangedEvent;
import com.ebee.trip.mapper.TripMapper;
import com.ebee.trip.repository.TripRepository;
import com.ebee.trip.service.TripOrderLifecycle;
import com.ebee.user.entity.User;
import com.ebee.user.enums.RoleType;
import com.ebee.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/** Driver app: trips (available / in progress, start, add orders, mileage, complete, trip expenses). */
@Slf4j
@Service
@RequiredArgsConstructor
public class DriverTripService {

    private final OrderRepository orderRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseMapper expenseMapper;
    private final DeliveryService deliveryService;
    private final DeliveryStateTransition deliveryStateTransition;
    private final AuditLogService auditLogService;
    private final StoragePort storagePort;
    private final ApplicationEventPublisher eventPublisher;
    private final TripRepository tripRepository;
    private final TripMapper tripMapper;
    private final TripOrderLifecycle tripOrderLifecycle;
    private final DriverContext driverContext;

    @Transactional(readOnly = true)
    public List<TripResponse> getAvailableTrips() {
        Driver driver = driverContext.getCurrentDriver();
        List<Trip> trips = tripRepository.findByDriverIdAndStatusIn(
                driver.getId(),
                List.of(TripStatus.PLANNED, TripStatus.IN_PROGRESS));
        return trips.stream().map(tripMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TripResponse getMyInProgressTrip() {
        Driver driver = driverContext.getCurrentDriver();
        return tripRepository.findByDriverIdAndStatus(driver.getId(), TripStatus.IN_PROGRESS)
                .map(tripMapper::toResponse)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public TripResponse getTripById(UUID tripId) {
        Driver driver = driverContext.getCurrentDriver();
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", "id", tripId));

        if (trip.getDriver() == null || !trip.getDriver().getId().equals(driver.getId())) {
            throw new AccessDeniedException("This trip is not assigned to you");
        }

        return tripMapper.toResponse(trip);
    }

    @Transactional
    public TripResponse startTrip(UUID tripId) {
        Driver driver = driverContext.getCurrentDriver();
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", "id", tripId));

        if (trip.getDriver() == null || !trip.getDriver().getId().equals(driver.getId())) {
            throw new AccessDeniedException("This trip is not assigned to you");
        }
        if (trip.getStatus() != TripStatus.PLANNED) {
            throw new IllegalStateException("Only PLANNING trips can be started");
        }

        tripRepository.findByDriverIdAndStatus(driver.getId(), TripStatus.IN_PROGRESS)
                .ifPresent(existing -> {
                    throw new IllegalStateException("You already have an active trip: " + existing.getName());
                });

        trip.setStatus(TripStatus.IN_PROGRESS);
        trip = tripRepository.save(trip);

        auditLogService.record(AuditAction.UPDATE, "Trip", trip.getId(),
                "Driver started trip '" + trip.getName() + "'");

        List<Order> orders = tripOrderLifecycle.getTripOrders(trip.getId());
        for (Order order : orders) {
            if (tripOrderLifecycle.isPendingForAssignment(order)) {
                DeliveryStateTransition.TransitionResult result =
                        deliveryStateTransition.transition(order, DeliveryStatus.ASSIGNED, "Trip started", false);
                orderRepository.save(order);
                eventPublisher.publishEvent(new DeliveryStatusChangedEvent(
                        this, order, result.previousStatus(), "Trip started", LocalDateTime.now()));
            }
        }

        deliveryService.recalculateDriverAvailability(driver.getUser().getId());

        eventPublisher.publishEvent(new TripStatusChangedEvent(this, trip, TripStatus.PLANNED, orders.size()));

        return tripMapper.toResponse(trip);
    }

    @Transactional
    public TripResponse addOrdersToTrip(UUID tripId, AddOrdersToTripRequest request) {
        Driver driver = driverContext.getCurrentDriver();
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", "id", tripId));

        if (trip.getDriver() == null || !trip.getDriver().getId().equals(driver.getId())) {
            throw new AccessDeniedException("This trip is not assigned to you");
        }
        if (trip.getStatus() == TripStatus.COMPLETED || trip.getStatus() == TripStatus.CANCELLED) {
            throw new IllegalStateException("Cannot modify orders on a " + trip.getStatus() + " trip");
        }

        if (request.getOrderIdsToAdd() != null && !request.getOrderIdsToAdd().isEmpty()) {
            for (UUID orderId : request.getOrderIdsToAdd()) {
                Order order = orderRepository.findById(orderId)
                        .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

                if (order.getTrip() != null && order.getTrip().getId().equals(tripId)) {
                    continue;
                }
                if (order.getDeliveryAssignedTo() != null
                        && !order.getDeliveryAssignedTo().getId().equals(driver.getUser().getId())) {
                    throw new IllegalStateException("Order " + order.getOrderNumber() + " is assigned to a different driver");
                }
                if (order.getTrip() != null) {
                    throw new IllegalStateException("Order " + order.getOrderNumber() + " is already on trip '" + order.getTrip().getName() + "'");
                }

                if (order.getStatus() != OrderStatus.SHIPPED) {
                    throw new IllegalStateException("Order " + order.getOrderNumber() + " must be in SHIPPED status to add to a trip");
                }

                order.setTrip(trip);
                order.setDeliveryAssignedTo(driver.getUser());
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

        eventPublisher.publishEvent(new TripOrdersChangedEvent(this, trip, buildOrderMarkers(trip)));

        return tripMapper.toResponse(trip);
    }

    @Transactional
    public TripResponse updateMileage(UUID tripId, BigDecimal mileageKm) {
        Driver driver = driverContext.getCurrentDriver();
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", "id", tripId));

        if (trip.getDriver() == null || !trip.getDriver().getId().equals(driver.getId())) {
            throw new AccessDeniedException("This trip is not assigned to you");
        }
        if (mileageKm == null || mileageKm.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Mileage cannot be negative");
        }

        trip.setMileageKm(mileageKm);
        trip = tripRepository.save(trip);

        auditLogService.record(AuditAction.UPDATE, "Trip", trip.getId(),
                "Mileage updated to " + mileageKm + " km on trip '" + trip.getName() + "'");

        return tripMapper.toResponse(trip);
    }

    @Transactional
    public TripResponse completeTrip(UUID tripId) {
        Driver driver = driverContext.getCurrentDriver();
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", "id", tripId));

        if (trip.getDriver() == null || !trip.getDriver().getId().equals(driver.getId())) {
            throw new AccessDeniedException("This trip is not assigned to you");
        }
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

        deliveryService.recalculateDriverAvailability(driver.getUser().getId());

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
                "Trip '" + trip.getName() + "' completed by driver");

        eventPublisher.publishEvent(new TripStatusChangedEvent(this, trip, previousStatus, orders.size()));
        eventPublisher.publishEvent(new TripOrdersChangedEvent(this, trip, List.of()));

        return tripMapper.toResponse(trip);
    }

    @Transactional
    public ExpenseResponse createTripExpense(UUID tripId, ExpenseRequest request, MultipartFile receipt) {
        Driver driver = driverContext.getCurrentDriver();
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", "id", tripId));

        if (trip.getDriver() == null || !trip.getDriver().getId().equals(driver.getId())) {
            throw new AccessDeniedException("This trip is not assigned to you");
        }
        if (trip.getStatus() == TripStatus.COMPLETED || trip.getStatus() == TripStatus.CANCELLED) {
            throw new IllegalStateException("Cannot add expenses to a " + trip.getStatus() + " trip");
        }

        User currentUser = driverContext.getCurrentUser();

        Expense.ExpenseBuilder builder = Expense.builder()
                .category(request.getCategory())
                .amount(request.getAmount())
                .description(request.getDescription())
                .dateIncurred(request.getDateIncurred())
                .createdBy(currentUser)
                .driver(driver)
                .trip(trip)
                .status(ExpenseStatus.PENDING);

        Expense expense = builder.build();

        if (receipt != null && !receipt.isEmpty()) {
            expense.setReceiptUrl(storagePort.uploadFile(receipt, "expenses", currentUser.getEmail() + "_Expense"));
        }

        expense = expenseRepository.save(expense);

        auditLogService.record(AuditAction.CREATE, "Expense", expense.getId(),
                "Driver expense " + expense.getCategory() + " LKR " + expense.getAmount() + " created on trip '" + trip.getName() + "'");

        return expenseMapper.toResponse(expense);
    }

    private List<TripOrderMarkerDto> buildOrderMarkers(Trip trip) {
        List<Order> orders = orderRepository.findByTripId(trip.getId());
        if (orders == null || orders.isEmpty()) {
            return List.of();
        }
        return orders.stream()
                .map(this::toTripOrderMarkerDto)
                .toList();
    }

    private TripOrderMarkerDto toTripOrderMarkerDto(Order order) {
        String customerName = null;
        String customerPhone = null;
        String street = null;
        String addressLine2 = null;
        String city = null;
        String postalCode = null;
        String district = null;
        String country = null;

        if (order.getCustomer() != null) {
            customerName = order.getCustomer().getFirstName() + " " + order.getCustomer().getLastName();
            customerPhone = order.getCustomer().getPhoneNumber();
            if (order.getCustomer().getAddress() != null) {
                var addr = order.getCustomer().getAddress();
                street = addr.getStreet();
                addressLine2 = addr.getAddressLine2();
                city = addr.getCity();
                postalCode = addr.getPostalCode();
                district = addr.getDistrict() != null ? addr.getDistrict().getDisplayName() : null;
                country = addr.getCountry();
            }
        }

        String formattedAddress = buildFormattedAddress(street, addressLine2, city, postalCode, district, country);

        return new TripOrderMarkerDto(
                order.getId(),
                order.getOrderNumber(),
                customerName,
                customerPhone,
                street,
                addressLine2,
                city,
                postalCode,
                district,
                country,
                order.getDeliveryStatus(),
                order.getStatus(),
                order.getTotalAmount(),
                formattedAddress
        );
    }

    private String buildFormattedAddress(String street, String addressLine2, String city,
                                         String postalCode, String district, String country) {
        StringBuilder sb = new StringBuilder();
        if (street != null) sb.append(street);
        if (addressLine2 != null) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(addressLine2);
        }
        if (city != null) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(city);
        }
        if (postalCode != null) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(postalCode);
        }
        if (district != null) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(district);
        }
        if (country != null) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(country);
        }
        return sb.toString();
    }

}
