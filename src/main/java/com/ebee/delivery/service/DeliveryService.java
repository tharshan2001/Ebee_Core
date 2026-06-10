package com.ebee.delivery.service;

import com.ebee.audit.enums.AuditAction;
import com.ebee.audit.service.AuditLogService;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.security.SecurityService;
import com.ebee.alert.enums.AlertType;
import com.ebee.commission.service.CommissionLifecycleService;
import com.ebee.delivery.dto.DeliveryResponse;
import com.ebee.notification.NotificationPort;
import com.ebee.delivery.dto.DeliveryStatusUpdateRequest;
import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.delivery.repository.DeliveryRepository;
import com.ebee.driver.entity.Driver;
import com.ebee.driver.enums.DriverAvailability;
import com.ebee.driver.repository.DriverRepository;
import com.ebee.inventory.service.InventoryService;
import com.ebee.order.entity.Order;
import com.ebee.order.repository.OrderRepository;
import com.ebee.stock.enums.StockMovementType;
import com.ebee.user.entity.User;
import com.ebee.user.enums.RoleType;
import com.ebee.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final DriverRepository driverRepository;
    private final SecurityService securityService;
    private final NotificationPort notificationService;
    private final InventoryService inventoryService;
    private final CommissionLifecycleService commissionLifecycleService;
    private final AuditLogService auditLogService;

    @Transactional
    public void initializeDelivery(Order order) {
        if (order.getDeliveryStatus() == null) {
            order.setDeliveryStatus(DeliveryStatus.PENDING);
        }
    }

    @Transactional
    public DeliveryResponse assignDelivery(UUID orderId, UUID driverId) {
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if (order.getDeliveryStatus() != DeliveryStatus.PENDING) {
            throw new IllegalArgumentException("Delivery can only be assigned when status is PENDING");
        }

        User driver = userRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", driverId));

        if (driver.getRole() != RoleType.DRIVER) {
            throw new IllegalArgumentException("User is not a driver");
        }

        Driver driverProfile = driverRepository.findByUserId(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver", "userId", driverId));

        if (driverProfile.getAvailability() == DriverAvailability.SUSPENDED) {
            throw new IllegalArgumentException("Cannot assign delivery — driver is suspended");
        }

        driverProfile.setAvailability(DriverAvailability.ON_TRIP);
        driverRepository.save(driverProfile);

        order.setDeliveryAssignedTo(driver);
        order.setDeliveryStatus(DeliveryStatus.ASSIGNED);
        Order savedOrder = orderRepository.save(order);

        String subject = "Delivery Assigned – " + savedOrder.getOrderNumber();
        String driverMsg = "Dear %s,\n\nYou have been assigned to deliver order %s in %s.".formatted(
                driver.getFirstName(), savedOrder.getOrderNumber(), savedOrder.getDistrict());
        notificationService.notifyUser(driver, subject, driverMsg);
        notificationService.createInternalAlert(driver, subject, driverMsg,
                AlertType.DELIVERY_ASSIGNED, savedOrder.getId(), "order");

        if (savedOrder.getCustomer() != null) {
            String customerMsg = "Dear %s,\n\nYour order %s has been assigned to a driver for delivery.".formatted(
                    savedOrder.getCustomer().getFirstName(), savedOrder.getOrderNumber());
            notificationService.notifyCustomer(savedOrder.getCustomer(), subject, customerMsg);
        }

        auditLogService.record(AuditAction.DELIVERY_ASSIGN, "Order", savedOrder.getId(),
                "Driver " + driver.getFirstName() + " assigned to deliver order "
                + savedOrder.getOrderNumber());

        return toDeliveryResponse(savedOrder);
    }

    @Transactional
    public DeliveryResponse updateDeliveryStatus(UUID orderId, DeliveryStatusUpdateRequest request) {
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        DeliveryStatus newStatus = request.getStatus();

        DeliveryStateMachine.TransitionResult result = DeliveryStateMachine.transition(
                order, newStatus, request.getReason(), securityService.isAdmin());

        if (result.wasDelivered()) {
            commissionLifecycleService.approveCommissionForOrder(order.getId());
        }

        if (result.wasFailedPermanent()) {
            order.getItems().forEach(item -> inventoryService.recordOrderStock(
                    item.getProduct().getId(),
                    item.getQuantity(),
                    StockMovementType.RETURN,
                    "Delivery permanently failed - Order " + order.getOrderNumber()
            ));
            commissionLifecycleService.cancelCommissionForOrder(order.getId());
        }

        Order savedOrder = orderRepository.save(order);

        auditLogService.record(AuditAction.DELIVERY_STATUS_CHANGE, "Order", savedOrder.getId(),
                "Delivery for order " + savedOrder.getOrderNumber() + " changed from "
                        + result.previousStatus() + " to " + newStatus);

        sendDeliveryNotifications(savedOrder, newStatus);

        return toDeliveryResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    public List<DeliveryResponse> getDeliveries(DeliveryStatus status, String district) {
        List<Order> orders;
        if (status != null) {
            orders = deliveryRepository.findByDeliveryStatus(status);
        } else {
            orders = orderRepository.findAll().stream()
                    .filter(o -> o.getDeliveryStatus() != null)
                    .toList();
        }
        if (district != null && !district.isBlank()) {
            orders = orders.stream()
                    .filter(o -> district.equals(o.getDistrict()))
                    .toList();
        }
        return orders.stream().map(this::toDeliveryResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<DeliveryResponse> getMyDeliveries() {
        UUID userId = securityService.getCurrentUserId();
        List<Order> orders = deliveryRepository.findByDeliveryAssignedToId(userId);
        return orders.stream().map(this::toDeliveryResponse).toList();
    }

    @Transactional(readOnly = true)
    public DeliveryResponse getDeliveryDetail(UUID orderId) {
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if (order.getDeliveryStatus() == null) {
            throw new ResourceNotFoundException("Delivery", "orderId", orderId);
        }

        return toDeliveryResponse(order);
    }

    private DeliveryResponse toDeliveryResponse(Order order) {
        DeliveryResponse response = new DeliveryResponse();
        response.setOrderId(order.getId());
        response.setOrderNumber(order.getOrderNumber());
        if (order.getCustomer() != null) {
            response.setCustomerName(order.getCustomer().getFirstName() + " " + order.getCustomer().getLastName());
            response.setCustomerPhone(order.getCustomer().getPhoneNumber());
        }
        response.setDistrict(order.getDistrict());
        response.setStatus(order.getDeliveryStatus());
        if (order.getDeliveryAssignedTo() != null) {
            response.setDriverId(order.getDeliveryAssignedTo().getId());
            response.setDriverName(order.getDeliveryAssignedTo().getFirstName() + " " + order.getDeliveryAssignedTo().getLastName());
        }
        response.setScheduledDate(order.getDeliveryScheduledDate());
        response.setNotes(order.getDeliveryNotes());
        response.setDeliveredAt(order.getDeliveredAt());
        response.setDeliveryFailureReason(order.getDeliveryFailureReason());
        return response;
    }

    private void sendDeliveryNotifications(Order order, DeliveryStatus newStatus) {
        if (order.getCustomer() != null) {
            String customerName = order.getCustomer().getFirstName();
            String customerMsg = switch (newStatus) {
                case PICKED_UP -> "Dear %s,\n\nYour order %s has been picked up by the driver.".formatted(
                        customerName, order.getOrderNumber());
                case IN_TRANSIT -> "Dear %s,\n\nYour order %s is on its way for delivery!".formatted(
                        customerName, order.getOrderNumber());
                case FAILED -> {
                    String reason = order.getDeliveryFailureReason();
                    String base = "Dear %s,\n\nUnfortunately, delivery of your order %s has failed.".formatted(
                            customerName, order.getOrderNumber());
                    yield reason != null ? base + "\nReason: " + reason : base;
                }
                case FAILED_PERMANENT -> {
                    String reason = order.getDeliveryFailureReason();
                    String base = "Dear %s,\n\nDelivery of your order %s has been permanently failed.".formatted(
                            customerName, order.getOrderNumber());
                    yield reason != null ? base + "\nReason: " + reason : base;
                }
                default -> null;
            };
            if (customerMsg != null) {
                String subject = "Delivery " + newStatus.name() + " – " + order.getOrderNumber();
                notificationService.notifyCustomer(order.getCustomer(), subject, customerMsg);
            }
        }

        if (newStatus == DeliveryStatus.FAILED || newStatus == DeliveryStatus.FAILED_PERMANENT) {
            String prefix = newStatus == DeliveryStatus.FAILED_PERMANENT ? "Delivery Permanently Failed" : "Delivery Failed";
            String subject = prefix + " – " + order.getOrderNumber();
            String reason = order.getDeliveryFailureReason();
            String msg = reason != null
                    ? "Delivery of order %s has %sfailed.\nReason: %s"
                    .formatted(order.getOrderNumber(),
                            newStatus == DeliveryStatus.FAILED_PERMANENT ? "permanently " : "", reason)
                    : "Delivery of order %s has %sfailed."
                    .formatted(order.getOrderNumber(),
                            newStatus == DeliveryStatus.FAILED_PERMANENT ? "permanently " : "");
            notificationService.createInternalAlert(order.getCreatedBy(), subject, msg,
                    AlertType.DELIVERY_FAILED, order.getId(), "order");
        }
    }
}
