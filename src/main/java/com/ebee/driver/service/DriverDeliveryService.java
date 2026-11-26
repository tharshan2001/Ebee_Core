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

/** Driver app: the driver's own deliveries (list, detail, status moves, proof of delivery). */
@Slf4j
@Service
@RequiredArgsConstructor
public class DriverDeliveryService {

    private final DriverRepository driverRepository;
    private final OrderRepository orderRepository;
    private final DeliveryService deliveryService;
    private final DeliveryStateTransition deliveryStateTransition;
    private final CommissionLifecycleService commissionLifecycleService;
    private final SecurityService securityService;
    private final AuditLogService auditLogService;
    private final StoragePort storagePort;
    private final ApplicationEventPublisher eventPublisher;
    private final TripRepository tripRepository;
    private final DriverContext driverContext;

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

        driverContext.assertDriverAssigned(order);
        return toDriverDeliveryResponse(order);
    }

    @Transactional
    public DriverDeliveryResponse updateDeliveryStatus(UUID orderId, DriverDeliveryStatusRequest request) {
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        driverContext.assertDriverAssigned(order);
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
            driverContext.recalculateDriverAvailability(securityService.getCurrentUserId());
        }

        if (request.getStatus() == DeliveryStatus.FAILED
                && order.getDeliveryAssignedTo() != null) {
            driverContext.recalculateDriverAvailability(order.getDeliveryAssignedTo().getId());
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

        driverContext.assertDriverAssigned(order);

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

        driverContext.recalculateDriverAvailability(securityService.getCurrentUserId());

        commissionLifecycleService.approveCommissionForOrder(savedOrder.getId());

        auditLogService.record(AuditAction.DELIVERY_STATUS_CHANGE, "Order", savedOrder.getId(),
                "Driver completed delivery for order " + savedOrder.getOrderNumber());

        deliveryService.sendDeliveryStatusNotification(savedOrder, DeliveryStatus.DELIVERED);

        eventPublisher.publishEvent(new DeliveryStatusChangedEvent(
                this, savedOrder, DeliveryStatus.IN_TRANSIT, null, LocalDateTime.now()));

        return toDriverDeliveryResponse(savedOrder);
    }

    private void assertDriverCanTransition(DeliveryStatus current, DeliveryStatus target) {
        Set<DeliveryStatus> allowed = switch (current) {
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

    /** Also used by the dashboard (DriverAppService). */
    public DriverDeliveryResponse toDriverDeliveryResponse(Order order) {
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

}
