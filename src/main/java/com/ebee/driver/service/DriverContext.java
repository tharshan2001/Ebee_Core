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

/** The signed-in driver, and the checks and availability update every driver-app action shares. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DriverContext {

    private final DriverRepository driverRepository;
    private final UserRepository userRepository;
    private final DeliveryRepository deliveryRepository;
    private final SecurityService securityService;

    public Driver getCurrentDriver() {
        UUID userId = securityService.getCurrentUserId();
        return driverRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver", "userId", userId));
    }

    public User getCurrentUser() {
        UUID userId = securityService.getCurrentUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    public void assertDriverAssigned(Order order) {
        UUID userId = securityService.getCurrentUserId();
        if (order.getDeliveryAssignedTo() == null
                || !order.getDeliveryAssignedTo().getId().equals(userId)) {
            throw new AccessDeniedException("This delivery is not assigned to you");
        }
    }

    public void recalculateDriverAvailability(UUID userId) {
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

}
