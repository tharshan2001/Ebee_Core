package com.ebee.delivery.service;

import com.ebee.audit.enums.AuditAction;
import com.ebee.audit.service.AuditLogService;
import com.ebee.common.enums.District;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.security.SecurityService;
import com.ebee.alert.enums.AlertType;
import com.ebee.commission.service.CommissionLifecycleService;
import org.springframework.security.access.AccessDeniedException;
import com.ebee.delivery.dto.DeliveryResponse;
import com.ebee.delivery.event.DeliveryStatusChangedEvent;
import com.ebee.notification.NotificationPort;
import com.ebee.notification.model.PushRequest;
import com.ebee.delivery.dto.DeliveryStatusUpdateRequest;
import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.delivery.repository.DeliveryRepository;
import com.ebee.driver.entity.Driver;
import com.ebee.driver.enums.DriverAvailability;
import com.ebee.driver.repository.DriverRepository;
import com.ebee.inventory.service.InventoryService;
import com.ebee.order.entity.Order;
import com.ebee.order.enums.OrderStatus;
import com.ebee.order.event.OrderStatusChangedEvent;
import com.ebee.order.repository.OrderRepository;
import com.ebee.stock.enums.StockMovementType;
import com.ebee.user.entity.User;
import com.ebee.user.enums.RoleType;
import com.ebee.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final ApplicationEventPublisher eventPublisher;

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

        if (order.getDeliveryAssignedTo() != null) {
            driverRepository.findByUserId(order.getDeliveryAssignedTo().getId())
                    .ifPresent(assignedDriver -> {
                        if (Boolean.FALSE.equals(assignedDriver.getIsActive())
                                || assignedDriver.getAvailability() == DriverAvailability.SUSPENDED) {
                            throw new IllegalStateException("Cannot update delivery — assigned driver is not active");
                        }
                    });
        }

        DeliveryStatus previousStatus = order.getDeliveryStatus();
        DeliveryStatus newStatus = request.getStatus();
        OrderStatus previousOrderStatus = order.getStatus();

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

        if (securityService.isAdmin() && previousDriver != null) {
            sendDriverStatusPush(previousDriver.getId(), savedOrder, previousStatus, newStatus);
        }

        eventPublisher.publishEvent(new DeliveryStatusChangedEvent(
                this, savedOrder, previousStatus, request.getReason(), LocalDateTime.now()));

        if (result.wasDelivered()) {
            eventPublisher.publishEvent(new OrderStatusChangedEvent(
                    this, savedOrder, previousOrderStatus, LocalDateTime.now()));
        }

        return toDeliveryResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    public List<DeliveryResponse> getDeliveries(DeliveryStatus status, String district, UUID tripId) {
        District districtEnum = (district != null && !district.isBlank())
                ? District.fromDisplayName(district)
                : null;

        List<Order> orders;
        if (tripId != null) {
            if (status != null) {
                orders = deliveryRepository.findByTripIdAndDeliveryStatus(tripId, status);
            } else {
                orders = deliveryRepository.findByTripId(tripId);
            }
        } else if (status != null && districtEnum != null) {
            orders = deliveryRepository.findByDeliveryStatusAndDistrict(status, districtEnum);
        } else if (status != null) {
            orders = deliveryRepository.findByDeliveryStatus(status);
        } else if (districtEnum != null) {
            orders = deliveryRepository.findByDeliveryStatusNotNullAndDistrict(districtEnum);
        } else {
            orders = deliveryRepository.findByDeliveryStatusNotNull();
        }
        return orders.stream().map(this::toDeliveryResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<DeliveryResponse> getMyDeliveries() {
        UUID userId = securityService.getCurrentUserId();

        if (!driverRepository.existsByUserId(userId)) {
            throw new AccessDeniedException("Only drivers can access their deliveries");
        }

        List<DeliveryStatus> activeStatuses = List.of(
                DeliveryStatus.ASSIGNED, DeliveryStatus.PICKED_UP, DeliveryStatus.IN_TRANSIT);
        List<Order> orders = deliveryRepository.findByDeliveryAssignedToIdAndActiveStatuses(userId, activeStatuses);
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
        if (order.getTrip() != null) {
            response.setTripId(order.getTrip().getId());
            response.setTripName(order.getTrip().getName());
        }
        response.setScheduledDate(order.getDeliveryScheduledDate());
        response.setNotes(order.getDeliveryNotes());
        response.setDriverNotes(order.getDriverNotes());
        response.setDeliveredAt(order.getDeliveredAt());
        response.setDeliveryFailureReason(order.getDeliveryFailureReason());
        response.setDeliveryPhotoUrl(order.getDeliveryPhotoUrl());
        response.setDeliverySignatureUrl(order.getDeliverySignatureUrl());
        return response;
    }

    public void sendDeliveryStatusNotification(Order order, DeliveryStatus newStatus) {
        sendDeliveryNotifications(order, newStatus);
    }

    private void sendDriverStatusPush(UUID driverUserId, Order order,
                                       DeliveryStatus previousStatus, DeliveryStatus newStatus) {
        driverRepository.findByUserId(driverUserId)
                .filter(d -> d.getFcmToken() != null)
                .ifPresent(d -> {
                    String title;
                    String body;
                    String type;

                    if (previousStatus == DeliveryStatus.FAILED && newStatus == DeliveryStatus.PENDING) {
                        title = "Delivery #" + order.getOrderNumber() + " Reopened";
                        body = "Delivery " + order.getOrderNumber() + " has been reopened. You can retry now.";
                        type = "delivery_retry";
                    } else if (newStatus == DeliveryStatus.FAILED || newStatus == DeliveryStatus.FAILED_PERMANENT) {
                        return;
                    } else {
                        String statusLabel = switch (newStatus) {
                            case PICKED_UP -> "Picked Up";
                            case IN_TRANSIT -> "In Transit";
                            case DELIVERED -> "Delivered";
                            case PENDING -> "Pending";
                            default -> null;
                        };
                        if (statusLabel == null) return;
                        title = "Delivery #" + order.getOrderNumber() + " " + statusLabel;
                        body = "Your delivery " + order.getOrderNumber() + " has been updated to " + statusLabel + ".";
                        type = "delivery_status";
                    }

                    notificationPort.sendPush(new PushRequest(
                            d.getFcmToken(), title, body,
                            Map.of("type", type,
                                    "orderId", order.getId().toString(),
                                    "orderNumber", order.getOrderNumber(),
                                    "status", newStatus.name())
                    ));
                });
    }

    private void sendDeliveryNotifications(Order order, DeliveryStatus newStatus) {
        if (order.getCustomer() != null) {
            String statusLabel = switch (newStatus) {
                case DELIVERED -> "Delivered";
                case FAILED -> "Failed";
                case FAILED_PERMANENT -> "Failed Permanently";
                default -> null;
            };

            if (statusLabel != null) {
                String reason = order.getDeliveryFailureReason();
                String messageHtml = switch (newStatus) {
                    case DELIVERED -> "<p>Your order <strong>"
                            + order.getOrderNumber() + "</strong> has been delivered successfully.</p>"
                            + "<p>Thank you for your purchase!</p>";
                    case FAILED -> "<p>Unfortunately, delivery of your order <strong>"
                            + order.getOrderNumber() + "</strong> has failed."
                            + (reason != null ? "</p><p>Reason: " + reason : "") + "</p>";
                    case FAILED_PERMANENT -> "<p>Delivery of your order <strong>"
                            + order.getOrderNumber() + "</strong> has been permanently failed."
                            + (reason != null ? "</p><p>Reason: " + reason : "") + "</p>";
                    default -> "";
                };
                String messageTxt = switch (newStatus) {
                    case DELIVERED -> "Your order " + order.getOrderNumber()
                            + " has been delivered successfully. Thank you for your purchase!";
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

            userRepository.findByRoleIn(List.of(RoleType.ADMIN, RoleType.SUPER_ADMIN)).stream()
                    .filter(admin -> !admin.getId().equals(order.getCreatedBy().getId()))
                    .forEach(admin -> notificationPort.createInternalAlert(admin, subject, msg,
                            AlertType.DELIVERY_FAILED, order.getId(), "order"));

            if (order.getDeliveryAssignedTo() != null) {
                driverRepository.findByUserId(order.getDeliveryAssignedTo().getId())
                        .filter(d -> d.getFcmToken() != null)
                        .ifPresent(d -> {
                            String title;
                            String body;
                            String type;
                            if (newStatus == DeliveryStatus.FAILED_PERMANENT) {
                                title = "Delivery #" + order.getOrderNumber() + " Permanently Failed";
                                body = reason != null
                                        ? "Your delivery has been permanently failed. Reason: " + reason
                                        : "Your delivery has been permanently failed.";
                                type = "delivery_failed_permanent";
                            } else {
                                title = "Delivery #" + order.getOrderNumber() + " Failed";
                                body = reason != null
                                        ? "Your delivery attempt failed. Reason: " + reason
                                        : "Your delivery attempt failed. Tap to retry.";
                                type = "delivery_failed";
                            }
                            notificationPort.sendPush(new PushRequest(
                                    d.getFcmToken(), title, body,
                                    Map.of("type", type,
                                            "orderId", order.getId().toString(),
                                            "orderNumber", order.getOrderNumber())
                            ));
                        });
            }
        }
    }
}
