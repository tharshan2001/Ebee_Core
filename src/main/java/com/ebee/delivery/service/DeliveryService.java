package com.ebee.delivery.service;

import com.ebee.audit.enums.AuditAction;
import com.ebee.audit.service.AuditLogService;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.security.SecurityService;
import com.ebee.alert.enums.AlertType;
import com.ebee.commission.service.CommissionLifecycleService;
import org.springframework.security.access.AccessDeniedException;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final DriverRepository driverRepository;
    private final SecurityService securityService;
    private final NotificationPort notificationPort;
    private final InventoryService inventoryService;
    private final CommissionLifecycleService commissionLifecycleService;
    private final AuditLogService auditLogService;
    private final DeliveryStateTransition deliveryStateTransition;

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

        order.setDeliveryAssignedTo(driver);
        order.setDeliveryStatus(DeliveryStatus.ASSIGNED);
        Order savedOrder = orderRepository.save(order);

        updateDriverAvailability(driverId);

        Map<String, String> driverP = new HashMap<>();
        driverP.put("driverName", driver.getFirstName());
        driverP.put("orderNumber", savedOrder.getOrderNumber());
        driverP.put("district", savedOrder.getDistrict() != null ? savedOrder.getDistrict() : "");
        String subject = "Delivery Assigned – " + savedOrder.getOrderNumber();
        notificationPort.notifyUser(driver, subject, "delivery-assigned-driver", driverP, Set.of());

        notificationPort.createInternalAlert(driver, subject, driverP.get("driverName")
                + ", you have been assigned to deliver order " + savedOrder.getOrderNumber()
                + " in " + savedOrder.getDistrict() + ".",
                AlertType.DELIVERY_ASSIGNED, savedOrder.getId(), "order");

        if (savedOrder.getCustomer() != null) {
            Map<String, String> customerP = new HashMap<>();
            customerP.put("firstName", savedOrder.getCustomer().getFirstName());
            customerP.put("orderNumber", savedOrder.getOrderNumber());
            notificationPort.notifyCustomer(savedOrder.getCustomer(), subject,
                    "delivery-assigned-customer", customerP, Set.of());
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

        if (!securityService.isAdmin()) {
            UUID currentUserId = securityService.getCurrentUserId();
            if (order.getDeliveryAssignedTo() == null ||
                    !order.getDeliveryAssignedTo().getId().equals(currentUserId)) {
                throw new AccessDeniedException("You can only update deliveries assigned to you");
            }
        }

        DeliveryStatus previousStatus = order.getDeliveryStatus();
        DeliveryStatus newStatus = request.getStatus();

        User previousDriver = order.getDeliveryAssignedTo();

        DeliveryStateTransition.TransitionResult result = deliveryStateTransition.transition(
                order, newStatus, request.getReason(), securityService.isAdmin());

        if ((newStatus == DeliveryStatus.FAILED || newStatus == DeliveryStatus.FAILED_PERMANENT)
                && previousDriver != null) {
            updateDriverAvailability(previousDriver.getId());
        }

        if (result.wasDelivered()) {
            if (previousDriver != null) {
                updateDriverAvailability(previousDriver.getId());
            }
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

    /**
     * Derive driver availability from actual active delivery count.
     * Must be called within an active transaction.
     * <p>
     * Rules (see DRIVER_ASSIGNMENT.md):
     * AVAILABLE — 0 orders with status ASSIGNED / PICKED_UP / IN_TRANSIT
     * ON_TRIP   — ≥1 orders with status ASSIGNED / PICKED_UP / IN_TRANSIT
     */
    private void updateDriverAvailability(UUID driverUserId) {
        List<DeliveryStatus> activeStatuses = List.of(
                DeliveryStatus.ASSIGNED, DeliveryStatus.PICKED_UP, DeliveryStatus.IN_TRANSIT);
        long activeCount = deliveryRepository.countByDeliveryAssignedToIdAndDeliveryStatusIn(
                driverUserId, activeStatuses);
        DriverAvailability newAvailability = activeCount > 0
                ? DriverAvailability.ON_TRIP
                : DriverAvailability.AVAILABLE;
        driverRepository.findByUserId(driverUserId).ifPresent(driver -> {
            driver.setAvailability(newAvailability);
            driverRepository.save(driver);
        });
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
            String statusLabel = switch (newStatus) {
                case PICKED_UP -> "Picked Up";
                case IN_TRANSIT -> "In Transit";
                case FAILED -> "Failed";
                case FAILED_PERMANENT -> "Failed Permanently";
                default -> null;
            };

            if (statusLabel != null) {
                String reason = order.getDeliveryFailureReason();
                String messageHtml = switch (newStatus) {
                    case PICKED_UP -> "<p>Your order <strong>" + order.getOrderNumber()
                            + "</strong> has been picked up by the driver.</p>";
                    case IN_TRANSIT -> "<p>Your order <strong>" + order.getOrderNumber()
                            + "</strong> is on its way for delivery!</p>";
                    case FAILED -> "<p>Unfortunately, delivery of your order <strong>"
                            + order.getOrderNumber() + "</strong> has failed."
                            + (reason != null ? "</p><p>Reason: " + reason : "") + "</p>";
                    case FAILED_PERMANENT -> "<p>Delivery of your order <strong>"
                            + order.getOrderNumber() + "</strong> has been permanently failed."
                            + (reason != null ? "</p><p>Reason: " + reason : "") + "</p>";
                    default -> "";
                };
                String messageTxt = switch (newStatus) {
                    case PICKED_UP -> "Your order " + order.getOrderNumber()
                            + " has been picked up by the driver.";
                    case IN_TRANSIT -> "Your order " + order.getOrderNumber()
                            + " is on its way for delivery!";
                    case FAILED -> "Unfortunately, delivery of your order " + order.getOrderNumber()
                            + " has failed." + (reason != null ? " Reason: " + reason : "");
                    case FAILED_PERMANENT -> "Delivery of your order " + order.getOrderNumber()
                            + " has been permanently failed." + (reason != null ? " Reason: " + reason : "");
                    default -> "";
                };

                Map<String, String> p = new HashMap<>();
                p.put("firstName", order.getCustomer().getFirstName());
                p.put("orderNumber", order.getOrderNumber());
                p.put("statusLabel", statusLabel);
                p.put("messageBlock", messageHtml);
                p.put("messageBlockTxt", messageTxt);
                String subject = "Delivery " + statusLabel + " – " + order.getOrderNumber();
                notificationPort.notifyCustomer(order.getCustomer(), subject,
                        "delivery-status-customer", p, Set.of("messageBlock"));
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
            notificationPort.createInternalAlert(order.getCreatedBy(), subject, msg,
                    AlertType.DELIVERY_FAILED, order.getId(), "order");
        }
    }
}
