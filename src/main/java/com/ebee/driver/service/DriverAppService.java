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

@Slf4j
@Service
@RequiredArgsConstructor
public class DriverAppService {

    private final DriverRepository driverRepository;
    private final DriverService driverService;
    private final UserRepository userRepository;
    private final DeliveryRepository deliveryRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseMapper expenseMapper;
    private final AlertService alertService;
    private final SecurityService securityService;
    private final AuditLogService auditLogService;
    private final StoragePort storagePort;
    private final DriverContext driverContext;
    private final DriverDeliveryService driverDeliveryService;

    public Driver getCurrentDriver() {
        return driverContext.getCurrentDriver();
    }

    @Transactional(readOnly = true)
    public String getCurrentDriverName() {
        Driver driver = driverContext.getCurrentDriver();
        return driver.getUser().getFirstName() + " " + driver.getUser().getLastName();
    }

    @Transactional(readOnly = true)
    public DriverDashboardResponse getDashboard() {
        Driver driver = driverContext.getCurrentDriver();
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
        response.setRecentDeliveries(recentOrders.stream().map(driverDeliveryService::toDriverDeliveryResponse).toList());
        return response;
    }

    @Transactional
    public DriverResponse updateMyAvailability(DriverAvailability availability) {
        if (availability == DriverAvailability.SUSPENDED) {
            throw new IllegalArgumentException("Cannot set availability to SUSPENDED");
        }
        Driver driver = driverContext.getCurrentDriver();
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
        Driver driver = driverContext.getCurrentDriver();
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
        Driver driver = driverContext.getCurrentDriver();
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

        Driver driver = driverContext.getCurrentDriver();
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
        User currentUser = driverContext.getCurrentUser();
        Driver driver = driverContext.getCurrentDriver();

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
        Driver driver = driverContext.getCurrentDriver();
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

    @Transactional
    public DriverProfileResponse uploadProfilePhoto(MultipartFile photo) {
        User user = driverContext.getCurrentUser();
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
        Driver driver = driverContext.getCurrentDriver();
        driver.setFcmToken(fcmToken);
        driverRepository.save(driver);
        log.info("FCM token registered for driver {}", driver.getUser().getEmail());
    }

}
