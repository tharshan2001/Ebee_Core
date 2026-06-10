package com.ebee.order.service;

import com.ebee.alert.enums.AlertType;
import com.ebee.commission.entity.Commission;
import com.ebee.notification.NotificationPort;
import com.ebee.order.entity.Order;
import com.ebee.order.util.OrderItemSummaryBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderNotificationService {

    private final NotificationPort notificationService;

    public void sendOrderCreated(Order order, Commission commission) {
        String itemSummary = OrderItemSummaryBuilder.buildItemSummary(order);

        if (order.getCustomer() != null) {
            String customerSubject = "Order Placed – " + order.getOrderNumber();
            String customerMessage = """
                    Dear %s,

                    Your order %s has been placed successfully.

                    Items: %s
                    Total: LKR %.2f

                    We will notify you once it's confirmed.
                    Thank you for choosing Ebee!
                    """.formatted(
                    OrderItemSummaryBuilder.customerDisplayName(order.getCustomer()),
                    order.getOrderNumber(),
                    itemSummary,
                    order.getTotalAmount()
            );
            notificationService.notifyCustomer(order.getCustomer(), customerSubject, customerMessage);
        }

        if (order.getAgent() != null) {
            String agentSubject = "New Order Assigned – " + order.getOrderNumber();
            String customerInfo = order.getCustomer() != null
                    ? "Customer: " + OrderItemSummaryBuilder.customerDisplayName(order.getCustomer())
                    : "Customer: Walk-in";
            String commissionInfo = commission != null
                    ? "Commission: LKR %.2f (%.2f%% rate)".formatted(
                            commission.getCommissionAmount(), commission.getCommissionRate())
                    : "";
            String agentMessage = """
                    Dear %s,

                    A new order %s has been assigned to you.

                    %s
                    Items: %s
                    Total: LKR %.2f
                    %s

                    Please process the order at your earliest convenience.
                    """.formatted(
                    order.getAgent().getUser().getFirstName(),
                    order.getOrderNumber(),
                    customerInfo,
                    itemSummary,
                    order.getTotalAmount(),
                    commissionInfo
            );
            notificationService.notifyAgent(order.getAgent(), agentSubject, agentMessage);
            notificationService.createInternalAlert(order.getAgent().getUser(), agentSubject, agentMessage,
                    AlertType.ORDER_CREATED, order.getId(), "order");

            if (commission != null) {
                String commissionSubject = "Commission Calculated – Order " + order.getOrderNumber();
                String commissionMsg = """
                        Dear %s,

                        A commission of LKR %.2f has been calculated for order %s.

                        Order Amount: LKR %.2f
                        Rate: %.2f%%
                        Commission: LKR %.2f

                        The commission will be processed once approved.
                        """.formatted(
                        order.getAgent().getUser().getFirstName(),
                        commission.getCommissionAmount(),
                        order.getOrderNumber(),
                        order.getTotalAmount(),
                        commission.getCommissionRate(),
                        commission.getCommissionAmount()
                );
                notificationService.notifyAgent(order.getAgent(), commissionSubject, commissionMsg);
                notificationService.createInternalAlert(order.getAgent().getUser(), commissionSubject, commissionMsg,
                        AlertType.COMMISSION_CALCULATED, commission.getId(), "commission");
            }
        }
    }

    public void sendOrderStatus(Order order) {
        if (order.getCustomer() != null) {
            String statusMessage = switch (order.getStatus()) {
                case CONFIRMED -> "has been confirmed and is being prepared.";
                case PROCESSING -> "is now being processed.";
                case SHIPPED -> "has been shipped and is on its way!";
                case DELIVERED -> "has been delivered. Thank you for shopping with us!";
                case RETURNED -> "has been returned. Please contact support if you have any questions.";
                default -> null;
            };

            if (statusMessage != null) {
                String subject = "Order " + order.getStatus().name() + " – " + order.getOrderNumber();
                String message = "Dear %s,\n\nYour order %s %s".formatted(
                        OrderItemSummaryBuilder.customerDisplayName(order.getCustomer()),
                        order.getOrderNumber(),
                        statusMessage
                );
                notificationService.notifyCustomer(order.getCustomer(), subject, message);
            }
        }

        if (order.getAgent() != null) {
            String statusMessage = switch (order.getStatus()) {
                case CONFIRMED -> "has been confirmed.";
                case PROCESSING -> "is now being processed.";
                case SHIPPED -> "has been shipped.";
                case DELIVERED -> "has been delivered.";
                case RETURNED -> "has been returned.";
                default -> null;
            };

            if (statusMessage != null) {
                String customerInfo = order.getCustomer() != null
                        ? OrderItemSummaryBuilder.customerDisplayName(order.getCustomer())
                        : "Walk-in";
                String subject = "Order " + order.getStatus().name() + " – " + order.getOrderNumber();
                String message = "Dear %s,\n\nOrder %s for %s %s".formatted(
                        order.getAgent().getUser().getFirstName(),
                        order.getOrderNumber(),
                        customerInfo,
                        statusMessage
                );
                notificationService.notifyAgent(order.getAgent(), subject, message);
                notificationService.createInternalAlert(order.getAgent().getUser(), subject, message,
                        AlertType.ORDER_STATUS_CHANGED, order.getId(), "order");
            }
        }
    }

    public void sendOrderCancelled(Order order) {
        if (order.getCustomer() != null) {
            String subject = "Order Cancelled – " + order.getOrderNumber();
            String message = """
                    Dear %s,

                    Your order %s has been cancelled.

                    If you need assistance, please contact our support team.
                    """.formatted(
                    OrderItemSummaryBuilder.customerDisplayName(order.getCustomer()),
                    order.getOrderNumber()
            );
            notificationService.notifyCustomer(order.getCustomer(), subject, message);
        }

        if (order.getAgent() != null) {
            String customerInfo = order.getCustomer() != null
                    ? OrderItemSummaryBuilder.customerDisplayName(order.getCustomer())
                    : "Walk-in";
            String subject = "Order Cancelled – " + order.getOrderNumber();
            String message = "Dear %s,\n\nOrder %s for %s has been cancelled.".formatted(
                    order.getAgent().getUser().getFirstName(),
                    order.getOrderNumber(),
                    customerInfo
            );
            notificationService.notifyAgent(order.getAgent(), subject, message);
            notificationService.createInternalAlert(order.getAgent().getUser(), subject, message,
                    AlertType.ORDER_CANCELLED, order.getId(), "order");
        }
    }
}
