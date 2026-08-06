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
import com.ebee.trip.mapper.TripMapper;
import com.ebee.trip.repository.TripRepository;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class DriverAppService {

    private final DriverRepository driverRepository;
    private final DriverService driverService;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final DeliveryRepository deliveryRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseMapper expenseMapper;
    private final DeliveryService deliveryService;
    private final DeliveryStateTransition deliveryStateTransition;
    private final CommissionLifecycleService commissionLifecycleService;
    private final AlertService alertService;
    private final SecurityService securityService;
    private final AuditLogService auditLogService;
    private final StoragePort storagePort;
    private final NotificationPort notificationPort;
    private final ApplicationEventPublisher eventPublisher;
    private final TripRepository tripRepository;
    private final TripMapper tripMapper;

    public Driver getCurrentDriver() {
        UUID userId = securityService.getCurrentUserId();
        return driverRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver", "userId", userId));
    }

    @Transactional(readOnly = true)
    public String getCurrentDriverName() {
        Driver driver = getCurrentDriver();
        return driver.getUser().getFirstName() + " " + driver.getUser().getLastName();
    }

    private User getCurrentUser() {
        UUID userId = securityService.getCurrentUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    @Transactional(readOnly = true)
    public DriverDashboardResponse getDashboard() {
        Driver driver = getCurrentDriver();
        UUID userId = driver.getUser().getId();

        List<DeliveryStatus> activeStatuses = List.of(
                DeliveryStatus.ASSIGNED, DeliveryStatus.PICKED_UP, DeliveryStatus.IN_TRANSIT);
        long activeCount = deliveryRepository.countByDeliveryAssignedToIdAndDeliveryStatusIn(userId, activeStatuses);

        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime todayEnd = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);

        List<Order> todayDeliveries = deliveryRepository.findByDeliveryAssignedToId(userId).stream()
                .filter(o -> o.getUpdatedAt() != null
                        && o.getUpdatedAt().isAfter(todayStart)
                        && o.getUpdatedAt().isBefore(todayEnd))
                .toList();

        long completedToday = todayDeliveries.stream()
                .filter(o -> o.getDeliveryStatus() == DeliveryStatus.DELIVERED)
                .count();
        long failedToday = todayDeliveries.stream()
                .filter(o -> o.getDeliveryStatus() == DeliveryStatus.FAILED
                        || o.getDeliveryStatus() == DeliveryStatus.FAILED_PERMANENT)
                .count();

        long unreadAlertsCount = alertService.getUnreadCount(userId);

        List<Order> recentOrders = deliveryRepository.findByDeliveryAssignedToId(userId).stream()
                .sorted(Comparator.comparing(Order::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .toList();

        DriverDashboardResponse response = new DriverDashboardResponse();
        response.setAvailability(driver.getAvailability());
        response.setActiveDeliveriesCount(activeCount);
        response.setCompletedTodayCount(completedToday);
        response.setFailedTodayCount(failedToday);
        response.setUnreadAlertsCount(unreadAlertsCount);
        response.setDriverName(driver.getUser().getFirstName() + " " + driver.getUser().getLastName());
        response.setRecentDeliveries(recentOrders.stream().map(this::toDriverDeliveryResponse).toList());
        return response;
    }

    @Transactional(readOnly = true)
    public Page<DriverDeliveryResponse> getMyDeliveries(DeliveryStatus status, Pageable pageable) {
        UUID userId = securityService.getCurrentUserId();
        UUID driverId = driverRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver", "userId", userId))
                .getId();

        Optional<Trip> activeTrip = tripRepository.findByDriverIdAndStatus(driverId, TripStatus.IN_PROGRESS);
        if (activeTrip.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        List<Order> orders = orderRepository.findByTripId(activeTrip.get().getId());
        if (status != null) {
            orders = orders.stream()
                    .filter(o -> o.getDeliveryStatus() == status)
                    .toList();
        }
        orders.sort(Comparator.comparing(Order::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())));

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), orders.size());
        if (start > orders.size()) {
            return new PageImpl<>(List.of(), pageable, orders.size());
        }
        List<DriverDeliveryResponse> pageContent = orders.subList(start, end).stream()
                .map(this::toDriverDeliveryResponse)
                .toList();
        return new PageImpl<>(pageContent, pageable, orders.size());
    }

    @Transactional(readOnly = true)
    public DriverDeliveryResponse getDeliveryDetail(UUID orderId) {
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        assertDriverAssigned(order);
        return toDriverDeliveryResponse(order);
    }

    @Transactional
    public DriverDeliveryResponse updateDeliveryStatus(UUID orderId, DriverDeliveryStatusRequest request) {
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        assertDriverAssigned(order);
        assertDriverCanTransition(order.getDeliveryStatus(), request.getStatus());

        if (request.getDriverNotes() != null) {
            order.setDriverNotes(request.getDriverNotes());
        }

        DeliveryStatus previousStatus = order.getDeliveryStatus();
        DeliveryStateTransition.TransitionResult result = deliveryStateTransition.transition(
                order, request.getStatus(), request.getReason(), false);

        Order savedOrder = orderRepository.save(order);

        if (result.wasDelivered()) {
            commissionLifecycleService.approveCommissionForOrder(savedOrder.getId());
            recalculateDriverAvailability(securityService.getCurrentUserId());
        }

        if (request.getStatus() == DeliveryStatus.FAILED
                && order.getDeliveryAssignedTo() != null) {
            recalculateDriverAvailability(order.getDeliveryAssignedTo().getId());
        }

        auditLogService.record(AuditAction.DELIVERY_STATUS_CHANGE, "Order", savedOrder.getId(),
                "Driver updated delivery for order " + savedOrder.getOrderNumber()
                        + " from " + previousStatus + " to " + request.getStatus());

        deliveryService.sendDeliveryStatusNotification(savedOrder, request.getStatus());

        eventPublisher.publishEvent(new DeliveryStatusChangedEvent(
                this, savedOrder, previousStatus, request.getReason(), LocalDateTime.now()));

        return toDriverDeliveryResponse(savedOrder);
    }

    @Transactional
    public DriverDeliveryResponse completeDelivery(UUID orderId, MultipartFile photo,
                                                    MultipartFile signature, String driverNotes) {
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        assertDriverAssigned(order);

        if (order.getDeliveryStatus() != DeliveryStatus.IN_TRANSIT) {
            throw new IllegalArgumentException(
                    "Delivery can only be completed from IN_TRANSIT. Current status: " + order.getDeliveryStatus()
                            + ". Update status to IN_TRANSIT first via updateDeliveryStatus.");
        }

        if (photo != null && !photo.isEmpty()) {
            String photoUrl = storagePort.uploadFile(photo, "deliveries", order.getOrderNumber() + "_Photo");
            order.setDeliveryPhotoUrl(photoUrl);
        }

        if (signature != null && !signature.isEmpty()) {
            String sigUrl = storagePort.uploadFile(signature, "deliveries", order.getOrderNumber() + "_Signature");
            order.setDeliverySignatureUrl(sigUrl);
        }

        if (driverNotes != null) {
            order.setDriverNotes(driverNotes);
        }

        DeliveryStateTransition.TransitionResult result = deliveryStateTransition.transition(
                order, DeliveryStatus.DELIVERED, null, false);

        Order savedOrder = orderRepository.save(order);

        recalculateDriverAvailability(securityService.getCurrentUserId());

        commissionLifecycleService.approveCommissionForOrder(savedOrder.getId());

        auditLogService.record(AuditAction.DELIVERY_STATUS_CHANGE, "Order", savedOrder.getId(),
                "Driver completed delivery for order " + savedOrder.getOrderNumber());

        deliveryService.sendDeliveryStatusNotification(savedOrder, DeliveryStatus.DELIVERED);

        eventPublisher.publishEvent(new DeliveryStatusChangedEvent(
                this, savedOrder, DeliveryStatus.IN_TRANSIT, null, LocalDateTime.now()));

        return toDriverDeliveryResponse(savedOrder);
    }

    @Transactional
    public DriverResponse updateMyAvailability(DriverAvailability availability) {
        if (availability == DriverAvailability.SUSPENDED) {
            throw new IllegalArgumentException("Cannot set availability to SUSPENDED");
        }
        Driver driver = getCurrentDriver();
        UUID userId = driver.getUser().getId();

        if (availability == DriverAvailability.AVAILABLE) {
            List<DeliveryStatus> activeStatuses = List.of(
                    DeliveryStatus.ASSIGNED, DeliveryStatus.PICKED_UP, DeliveryStatus.IN_TRANSIT);
            long activeCount = deliveryRepository.countByDeliveryAssignedToIdAndDeliveryStatusIn(userId, activeStatuses);
            if (activeCount > 0) {
                throw new IllegalArgumentException("Cannot set AVAILABLE while you have active deliveries");
            }
        }

        return driverService.updateAvailability(driver.getId(),
                new com.ebee.driver.dto.AvailabilityRequest(availability));
    }

    @Transactional(readOnly = true)
    public DriverProfileResponse getMyProfile() {
        Driver driver = getCurrentDriver();
        User user = driver.getUser();
        DriverProfileResponse response = new DriverProfileResponse();
        response.setDriverId(driver.getId());
        response.setUserId(user.getId());
        response.setEmail(user.getEmail());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setPhoneNumber(user.getPhoneNumber());
        response.setLicenseNumber(driver.getLicenseNumber());
        response.setAvailability(driver.getAvailability());
        response.setIsActive(driver.getIsActive());
        response.setProfilePictureUrl(user.getProfilePictureUrl());
        response.setProfilePictureName(user.getProfilePictureName());
        response.setCreatedAt(driver.getCreatedAt());
        response.setUpdatedAt(driver.getUpdatedAt());
        return response;
    }

    @Transactional
    public DriverProfileResponse updateMyProfile(DriverProfileRequest request) {
        Driver driver = getCurrentDriver();
        User user = driver.getUser();

        if (request.getFirstName() != null) user.setFirstName(request.getFirstName());
        if (request.getLastName() != null) user.setLastName(request.getLastName());
        if (request.getPhoneNumber() != null) user.setPhoneNumber(PhoneUtils.normalize(request.getPhoneNumber()));
        if (request.getLicenseNumber() != null) driver.setLicenseNumber(request.getLicenseNumber());

        userRepository.save(user);
        driverRepository.save(driver);

        auditLogService.record(AuditAction.DRIVER_UPDATE, "Driver", driver.getId(),
                "Driver " + user.getEmail() + " updated their profile");

        return getMyProfile();
    }

    @Transactional(readOnly = true)
    public DriverStatsResponse getMyStats() {
        UUID userId = securityService.getCurrentUserId();

        List<Order> myDeliveries = deliveryRepository.findByDeliveryAssignedToId(userId);

        long completed = myDeliveries.stream()
                .filter(o -> o.getDeliveryStatus() == DeliveryStatus.DELIVERED)
                .count();
        long failed = myDeliveries.stream()
                .filter(o -> o.getDeliveryStatus() == DeliveryStatus.FAILED
                        || o.getDeliveryStatus() == DeliveryStatus.FAILED_PERMANENT)
                .count();

        LocalDateTime weekStart = LocalDateTime.of(LocalDate.now().minusDays(LocalDate.now().getDayOfWeek().getValue() - 1), LocalTime.MIN);
        LocalDateTime monthStart = LocalDateTime.of(LocalDate.now().withDayOfMonth(1), LocalTime.MIN);

        long thisWeek = myDeliveries.stream()
                .filter(o -> o.getDeliveryStatus() == DeliveryStatus.DELIVERED
                        && o.getDeliveredAt() != null && o.getDeliveredAt().isAfter(weekStart))
                .count();
        long thisMonth = myDeliveries.stream()
                .filter(o -> o.getDeliveryStatus() == DeliveryStatus.DELIVERED
                        && o.getDeliveredAt() != null && o.getDeliveredAt().isAfter(monthStart))
                .count();

        BigDecimal avgHours = BigDecimal.ZERO;
        List<Order> delivered = myDeliveries.stream()
                .filter(o -> o.getDeliveryStatus() == DeliveryStatus.DELIVERED
                        && o.getDeliveredAt() != null && o.getCreatedAt() != null)
                .toList();
        if (!delivered.isEmpty()) {
            double totalHours = delivered.stream()
                    .mapToLong(o -> java.time.Duration.between(o.getCreatedAt(), o.getDeliveredAt()).toMinutes())
                    .sum();
            avgHours = BigDecimal.valueOf(totalHours / (double) delivered.size() / 60.0)
                    .setScale(2, java.math.RoundingMode.HALF_UP);
        }

        Driver driver = getCurrentDriver();
        List<Expense> driverExpenses = expenseRepository.findByDriverIdOrderByCreatedAtDesc(driver.getId());
        BigDecimal totalExpenses = driverExpenses.stream()
                .filter(e -> e.getStatus() == ExpenseStatus.APPROVED)
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        DriverStatsResponse response = new DriverStatsResponse();
        response.setTotalDeliveriesCompleted(completed);
        response.setTotalDeliveriesFailed(failed);
        response.setDeliveriesThisWeek(thisWeek);
        response.setDeliveriesThisMonth(thisMonth);
        response.setAverageDeliveryTimeHours(avgHours);
        response.setTotalExpensesClaimed(totalExpenses);
        response.setTotalExpensesCount(driverExpenses.size());
        return response;
    }

    @Transactional
    public ExpenseResponse createExpense(ExpenseRequest request, MultipartFile receipt) {
        User currentUser = getCurrentUser();
        Driver driver = getCurrentDriver();

        Expense expense = Expense.builder()
                .category(request.getCategory())
                .amount(request.getAmount())
                .description(request.getDescription())
                .dateIncurred(request.getDateIncurred())
                .createdBy(currentUser)
                .driver(driver)
                .status(ExpenseStatus.PENDING)
                .build();

        if (receipt != null && !receipt.isEmpty()) {
            expense.setReceiptUrl(storagePort.uploadFile(receipt, "expenses", currentUser.getEmail() + "_Expense"));
        }

        expense = expenseRepository.save(expense);

        auditLogService.record(AuditAction.CREATE, "Expense", expense.getId(),
                "Driver expense " + expense.getCategory() + " LKR " + expense.getAmount() + " created");

        return expenseMapper.toResponse(expense);
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> getMyExpenses(ExpenseStatus status) {
        Driver driver = getCurrentDriver();
        List<Expense> expenses;
        if (status != null) {
            expenses = expenseRepository.findByDriverIdOrderByCreatedAtDesc(driver.getId()).stream()
                    .filter(e -> e.getStatus() == status)
                    .toList();
        } else {
            expenses = expenseRepository.findByDriverIdOrderByCreatedAtDesc(driver.getId());
        }
        return expenseMapper.toResponseList(expenses);
    }

    @Transactional(readOnly = true)
    public List<TripResponse> getAvailableTrips() {
        Driver driver = getCurrentDriver();
        List<Trip> trips = tripRepository.findByDriverIdAndStatusIn(
                driver.getId(),
                List.of(TripStatus.PLANNED, TripStatus.IN_PROGRESS));
        return trips.stream().map(tripMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TripResponse getMyInProgressTrip() {
        Driver driver = getCurrentDriver();
        return tripRepository.findByDriverIdAndStatus(driver.getId(), TripStatus.IN_PROGRESS)
                .map(tripMapper::toResponse)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public TripResponse getTripById(UUID tripId) {
        Driver driver = getCurrentDriver();
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", "id", tripId));

        if (trip.getDriver() == null || !trip.getDriver().getId().equals(driver.getId())) {
            throw new AccessDeniedException("This trip is not assigned to you");
        }

        return tripMapper.toResponse(trip);
    }

    @Transactional
    public TripResponse startTrip(UUID tripId) {
        Driver driver = getCurrentDriver();
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

        for (Order order : trip.getOrders()) {
            if (order.getDeliveryStatus() == DeliveryStatus.PENDING) {
                DeliveryStateTransition.TransitionResult result =
                        deliveryStateTransition.transition(order, DeliveryStatus.ASSIGNED, "Trip started", false);
                orderRepository.save(order);
                eventPublisher.publishEvent(new DeliveryStatusChangedEvent(
                        this, order, result.previousStatus(), "Trip started", LocalDateTime.now()));
            }
        }

        return tripMapper.toResponse(trip);
    }

    @Transactional
    public TripResponse addOrdersToTrip(UUID tripId, AddOrdersToTripRequest request) {
        Driver driver = getCurrentDriver();
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

                if (order.getStatus() != OrderStatus.SHIPPED && order.getStatus() != OrderStatus.FAILED) {
                    throw new IllegalStateException("Order " + order.getOrderNumber() + " must be in SHIPPED or FAILED status to add to a trip");
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

                order.setTrip(null);
                orderRepository.save(order);
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
        Driver driver = getCurrentDriver();
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", "id", tripId));

        if (trip.getDriver() == null || !trip.getDriver().getId().equals(driver.getId())) {
            throw new AccessDeniedException("This trip is not assigned to you");
        }

        trip.setMileageKm(mileageKm);
        trip = tripRepository.save(trip);

        auditLogService.record(AuditAction.UPDATE, "Trip", trip.getId(),
                "Mileage updated to " + mileageKm + " km on trip '" + trip.getName() + "'");

        return tripMapper.toResponse(trip);
    }

    @Transactional
    public TripResponse completeTrip(UUID tripId) {
        Driver driver = getCurrentDriver();
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", "id", tripId));

        if (trip.getDriver() == null || !trip.getDriver().getId().equals(driver.getId())) {
            throw new AccessDeniedException("This trip is not assigned to you");
        }
        if (trip.getStatus() != TripStatus.IN_PROGRESS) {
            throw new IllegalStateException("Only IN_PROGRESS trips can be completed");
        }

        trip.setStatus(TripStatus.COMPLETED);
        trip = tripRepository.save(trip);

        auditLogService.record(AuditAction.UPDATE, "Trip", trip.getId(),
                "Trip '" + trip.getName() + "' completed by driver");

        return tripMapper.toResponse(trip);
    }

    @Transactional
    public ExpenseResponse createTripExpense(UUID tripId, ExpenseRequest request, MultipartFile receipt) {
        Driver driver = getCurrentDriver();
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", "id", tripId));

        if (trip.getDriver() == null || !trip.getDriver().getId().equals(driver.getId())) {
            throw new AccessDeniedException("This trip is not assigned to you");
        }
        if (trip.getStatus() == TripStatus.COMPLETED || trip.getStatus() == TripStatus.CANCELLED) {
            throw new IllegalStateException("Cannot add expenses to a " + trip.getStatus() + " trip");
        }

        User currentUser = getCurrentUser();

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

    @Transactional
    public DriverProfileResponse uploadProfilePhoto(MultipartFile photo) {
        User user = getCurrentUser();
        if (photo != null && !photo.isEmpty()) {
            String photoUrl = storagePort.uploadFile(photo, "profiles", user.getEmail() + "_Profile");
            user.setProfilePictureUrl(photoUrl);
            user.setProfilePictureName(photo.getOriginalFilename());
            userRepository.save(user);
        }
        return getMyProfile();
    }

    @Transactional
    public void registerDeviceToken(String fcmToken) {
        Driver driver = getCurrentDriver();
        driver.setFcmToken(fcmToken);
        driverRepository.save(driver);
        log.info("FCM token registered for driver {}", driver.getUser().getEmail());
    }

    private void assertDriverAssigned(Order order) {
        UUID userId = securityService.getCurrentUserId();
        if (order.getDeliveryAssignedTo() == null
                || !order.getDeliveryAssignedTo().getId().equals(userId)) {
            throw new AccessDeniedException("This delivery is not assigned to you");
        }
    }

    private void recalculateDriverAvailability(UUID userId) {
        List<DeliveryStatus> activeStatuses = List.of(
                DeliveryStatus.ASSIGNED, DeliveryStatus.PICKED_UP, DeliveryStatus.IN_TRANSIT);
        long activeCount = deliveryRepository.countByDeliveryAssignedToIdAndDeliveryStatusIn(userId, activeStatuses);
        DriverAvailability newAvailability = activeCount > 0 ? DriverAvailability.ON_TRIP : DriverAvailability.AVAILABLE;
        driverRepository.findByUserId(userId).ifPresent(d -> {
            if (d.getAvailability() != newAvailability) {
                d.setAvailability(newAvailability);
                driverRepository.save(d);
            }
        });
    }

    private void assertDriverCanTransition(DeliveryStatus current, DeliveryStatus target) {
        Set<DeliveryStatus> allowed = switch (current) {
            case PENDING -> Set.of(DeliveryStatus.PICKED_UP, DeliveryStatus.FAILED);
            case ASSIGNED -> Set.of(DeliveryStatus.PICKED_UP, DeliveryStatus.FAILED);
            case PICKED_UP -> Set.of(DeliveryStatus.IN_TRANSIT, DeliveryStatus.FAILED);
            case IN_TRANSIT -> Set.of(DeliveryStatus.DELIVERED, DeliveryStatus.FAILED);
            case FAILED -> Set.of(DeliveryStatus.PENDING);
            default -> Set.of();
        };
        if (!allowed.contains(target)) {
            throw new IllegalArgumentException(
                    "Cannot transition from " + current + " to " + target);
        }
    }

    private DriverDeliveryResponse toDriverDeliveryResponse(Order order) {
        DriverDeliveryResponse response = new DriverDeliveryResponse();
        response.setOrderId(order.getId());
        response.setOrderNumber(order.getOrderNumber());
        response.setDeliveryStatus(order.getDeliveryStatus());
        response.setOrderStatus(order.getStatus());
        response.setDistrict(order.getDistrict());
        response.setNotes(order.getNotes());
        response.setDeliveryNotes(order.getDeliveryNotes());
        response.setDriverNotes(order.getDriverNotes());
        response.setDeliveryFailureReason(order.getDeliveryFailureReason());
        response.setDeliveryPhotoUrl(order.getDeliveryPhotoUrl());
        response.setDeliverySignatureUrl(order.getDeliverySignatureUrl());
        response.setDeliveredAt(order.getDeliveredAt());
        response.setTotalAmount(order.getTotalAmount());
        response.setPaidAmount(order.getPaidAmount());
        response.setOutstandingBalance(order.getOutstandingBalance());
        response.setPaymentStatus(order.getPaymentStatus());
        response.setScheduledDate(order.getDeliveryScheduledDate());
        response.setCreatedAt(order.getCreatedAt());

        if (order.getDeliveryAssignedTo() != null) {
            response.setDriverId(order.getDeliveryAssignedTo().getId());
            response.setDriverName(order.getDeliveryAssignedTo().getFirstName()
                    + " " + order.getDeliveryAssignedTo().getLastName());
        }

        if (order.getCustomer() != null) {
            response.setCustomerName(order.getCustomer().getFirstName()
                    + " " + order.getCustomer().getLastName());
            response.setCustomerPhone(order.getCustomer().getPhoneNumber());
            if (order.getCustomer().getAddress() != null) {
                var a = order.getCustomer().getAddress();
                response.setCustomerStreet(a.getStreet());
                response.setCustomerAddressLine2(a.getAddressLine2());
                response.setCustomerCity(a.getCity());
                response.setCustomerPostalCode(a.getPostalCode());
                response.setCustomerDistrict(a.getDistrict() != null
                        ? a.getDistrict().getDisplayName() : null);
                response.setCustomerCountry(a.getCountry());
            }
        }

        if (order.getItems() != null) {
            response.setItems(order.getItems().stream().map(this::toItemInfo).toList());
        }

        return response;
    }

    private DriverDeliveryResponse.OrderItemInfo toItemInfo(OrderItem item) {
        DriverDeliveryResponse.OrderItemInfo info = new DriverDeliveryResponse.OrderItemInfo();
        info.setProductId(item.getProduct().getId());
        info.setProductName(item.getProduct().getName());
        info.setProductItemCode(item.getProduct().getItemCode());
        info.setImageUrl(item.getProduct().getImages().isEmpty()
                ? null
                : item.getProduct().getImages().getFirst().getImageUrl());
        info.setQuantity(item.getQuantity());
        info.setUnitPrice(item.getUnitPrice());
        info.setSubtotal(item.getSubtotal());
        return info;
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
