package com.ebee.order.service;

import com.ebee.alert.enums.AlertType;
import com.ebee.commission.entity.Commission;
import com.ebee.common.util.NumberUtils;
import com.ebee.notification.NotificationPort;
import com.ebee.order.entity.Order;
import com.ebee.order.enums.OrderStatus;
import com.ebee.order.util.OrderItemSummaryBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrderNotificationService {

    private static final Set<String> HTML_KEYS = Set.of(
            "itemSummaryHtml", "savingsBlock", "commissionBlock", "savingsContext"
    );

    private final NotificationPort notificationPort;

    public void sendOrderCreated(Order order, Commission commission) {
        BigDecimal totalSavings = OrderItemSummaryBuilder.totalSavings(order);
        boolean hasSavings = totalSavings.compareTo(BigDecimal.ZERO) > 0;
        BigDecimal originalAmount = OrderItemSummaryBuilder.totalOriginalAmount(order);
        BigDecimal totalAmount = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;

        String itemSummaryHtml = OrderItemSummaryBuilder.buildItemSummaryHtml(order);
        String itemSummaryBullet = OrderItemSummaryBuilder.buildItemSummaryBullet(order);

        String savingsBlockHtml = hasSavings
                ? "<div class=\"discount-breakdown\"><p><strong>You saved LKR "
                + NumberUtils.formatAmount(totalSavings) + " on this order!</strong></p></div>"
                : "";
        String savingsBlockTxt = hasSavings
                ? "You saved LKR " + NumberUtils.formatAmount(totalSavings) + " on this order."
                : "";

        if (order.getCustomer() != null) {
            Map<String, String> p = new HashMap<>();
            p.put("firstName", OrderItemSummaryBuilder.customerDisplayName(order.getCustomer()));
            p.put("orderNumber", order.getOrderNumber());
            p.put("itemSummaryHtml", itemSummaryHtml);
            p.put("itemSummary", itemSummaryBullet);
            p.put("subtotal", NumberUtils.formatAmount(originalAmount));
            p.put("discount", NumberUtils.formatAmount(totalSavings));
            p.put("totalAmount", NumberUtils.formatAmount(totalAmount));
            p.put("savingsBlock", savingsBlockHtml);
            p.put("savingsBlockTxt", savingsBlockTxt);
            notificationPort.notifyCustomer(order.getCustomer(),
                    "Order Placed – " + order.getOrderNumber(),
                    "order-created-customer", p, HTML_KEYS);
        }

        if (order.getAgent() != null) {
            String agentName = order.getAgent().getUser().getFirstName();
            String customerName = order.getCustomer() != null
                    ? OrderItemSummaryBuilder.customerDisplayName(order.getCustomer())
                    : "Walk-in";

            String commissionBlockHtml = commission != null
                    ? "<div class=\"commission-box\"><p><strong>Commission:</strong> LKR "
                    + NumberUtils.formatAmount(commission.getCommissionAmount())
                    + " (" + commission.getCommissionRate().toPlainString() + "% rate)</p></div>"
                    : "";
            String commissionBlockTxt = commission != null
                    ? "Commission: LKR " + NumberUtils.formatAmount(commission.getCommissionAmount())
                    + " (" + commission.getCommissionRate().toPlainString() + "% rate)."
                    : "";

            Map<String, String> p = new HashMap<>();
            p.put("agentName", agentName);
            p.put("customerName", customerName);
            p.put("orderNumber", order.getOrderNumber());
            p.put("itemSummaryHtml", itemSummaryHtml);
            p.put("itemSummary", itemSummaryBullet);
            p.put("subtotal", NumberUtils.formatAmount(originalAmount));
            p.put("discount", NumberUtils.formatAmount(totalSavings));
            p.put("totalAmount", NumberUtils.formatAmount(totalAmount));
            p.put("savingsBlock", savingsBlockHtml);
            p.put("savingsBlockTxt", savingsBlockTxt);
            p.put("commissionBlock", commissionBlockHtml);
            p.put("commissionBlockTxt", commissionBlockTxt);
            notificationPort.notifyAgent(order.getAgent(),
                    "New Order Assigned – " + order.getOrderNumber(),
                    "order-created-agent", p, HTML_KEYS);

            String alertMsg = "Order " + order.getOrderNumber() + " assigned for " + customerName
                    + ". Total: LKR " + NumberUtils.formatAmount(totalAmount);
            notificationPort.createInternalAlert(order.getAgent().getUser(),
                    "New Order Assigned – " + order.getOrderNumber(), alertMsg,
                    AlertType.ORDER_CREATED, order.getId(), "order");

            if (commission != null) {
                String savingsContextHtml = hasSavings
                        ? "<p><strong>Subtotal (before discount):</strong> LKR "
                        + NumberUtils.formatAmount(originalAmount) + "</p>\n"
                        + "<p class=\"savings\"><strong>Customer Savings:</strong> LKR "
                        + NumberUtils.formatAmount(totalSavings) + "</p>"
                        : "";
                String savingsContextTxt = hasSavings
                        ? "Subtotal: LKR " + NumberUtils.formatAmount(originalAmount)
                        + ", Customer Savings: LKR " + NumberUtils.formatAmount(totalSavings) + "."
                        : "";

                Map<String, String> cp = new HashMap<>();
                cp.put("agentName", agentName);
                cp.put("orderNumber", order.getOrderNumber());
                cp.put("totalAmount", NumberUtils.formatAmount(totalAmount));
                cp.put("commissionAmount", NumberUtils.formatAmount(commission.getCommissionAmount()));
                cp.put("commissionRate", commission.getCommissionRate().toPlainString());
                cp.put("savingsContext", savingsContextHtml);
                cp.put("savingsContextTxt", savingsContextTxt);
                cp.put("originalAmount", NumberUtils.formatAmount(originalAmount));
                cp.put("totalSavings", NumberUtils.formatAmount(totalSavings));
                notificationPort.notifyAgent(order.getAgent(),
                        "Commission Calculated – Order " + order.getOrderNumber(),
                        "order-commission-calculated", cp, HTML_KEYS);

                String commissionAlert = "Commission LKR " + NumberUtils.formatAmount(commission.getCommissionAmount())
                        + " at " + commission.getCommissionRate().toPlainString()
                        + "% for order " + order.getOrderNumber() + ".";
                notificationPort.createInternalAlert(order.getAgent().getUser(),
                        "Commission Calculated – Order " + order.getOrderNumber(), commissionAlert,
                        AlertType.COMMISSION_CALCULATED, commission.getId(), "commission");
            }
        }
    }

    public void sendOrderStatus(Order order) {
        boolean isMajor = order.getStatus() == OrderStatus.DELIVERED;

        if (order.getCustomer() != null && isMajor) {
            String statusLabel = "Delivered";

            Map<String, String> p = new HashMap<>();
            p.put("firstName", OrderItemSummaryBuilder.customerDisplayName(order.getCustomer()));
            p.put("orderNumber", order.getOrderNumber());
            p.put("statusLabel", statusLabel);
            notificationPort.notifyCustomer(order.getCustomer(),
                    "Order " + statusLabel + " – " + order.getOrderNumber(),
                    "order-status-customer", p, Set.of());
        }

        if (order.getAgent() != null) {
            String statusLabel = switch (order.getStatus()) {
                case CONFIRMED -> "Confirmed";
                case PROCESSING -> "Processing";
                case SHIPPED -> "Shipped";
                case DELIVERED -> "Delivered";
                case RETURNED -> "Returned";
                default -> null;
            };

            if (statusLabel != null) {
                String customerName = order.getCustomer() != null
                        ? OrderItemSummaryBuilder.customerDisplayName(order.getCustomer())
                        : "Walk-in";

                if (isMajor) {
                    Map<String, String> p = new HashMap<>();
                    p.put("agentName", order.getAgent().getUser().getFirstName());
                    p.put("customerName", customerName);
                    p.put("orderNumber", order.getOrderNumber());
                    p.put("statusLabel", statusLabel);
                    notificationPort.notifyAgent(order.getAgent(),
                            "Order " + statusLabel + " – " + order.getOrderNumber(),
                            "order-status-agent", p, Set.of());
                }

                String alertMsg = "Order " + order.getOrderNumber() + " for " + customerName
                        + " is now " + statusLabel + ".";
                notificationPort.createInternalAlert(order.getAgent().getUser(),
                        "Order " + statusLabel + " – " + order.getOrderNumber(), alertMsg,
                        AlertType.ORDER_STATUS_CHANGED, order.getId(), "order");
            }
        }
    }

    public void sendOrderCancelled(Order order) {
        if (order.getCustomer() != null) {
            Map<String, String> p = new HashMap<>();
            p.put("firstName", OrderItemSummaryBuilder.customerDisplayName(order.getCustomer()));
            p.put("orderNumber", order.getOrderNumber());
            notificationPort.notifyCustomer(order.getCustomer(),
                    "Order Cancelled – " + order.getOrderNumber(),
                    "order-cancelled-customer", p, Set.of());
        }

        if (order.getAgent() != null) {
            String customerName = order.getCustomer() != null
                    ? OrderItemSummaryBuilder.customerDisplayName(order.getCustomer())
                    : "Walk-in";
            Map<String, String> p = new HashMap<>();
            p.put("agentName", order.getAgent().getUser().getFirstName());
            p.put("customerName", customerName);
            p.put("orderNumber", order.getOrderNumber());
            notificationPort.notifyAgent(order.getAgent(),
                    "Order Cancelled – " + order.getOrderNumber(),
                    "order-cancelled-agent", p, Set.of());

            String alertMsg = "Order " + order.getOrderNumber() + " for " + customerName + " has been cancelled.";
            notificationPort.createInternalAlert(order.getAgent().getUser(),
                    "Order Cancelled – " + order.getOrderNumber(), alertMsg,
                    AlertType.ORDER_CANCELLED, order.getId(), "order");
        }
    }
}
