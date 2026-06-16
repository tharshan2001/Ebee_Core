package com.ebee.order.util;

import com.ebee.common.util.NumberUtils;
import com.ebee.customer.entity.Customer;
import com.ebee.order.entity.Order;
import com.ebee.order.entity.OrderItem;

import java.math.BigDecimal;
import java.util.stream.Collectors;

public final class OrderItemSummaryBuilder {

    private OrderItemSummaryBuilder() {}

    public static String buildItemSummary(Order order) {
        return order.getItems().stream()
                .map(item -> item.getProduct().getName() + " (x" + item.getQuantity() + ")")
                .collect(Collectors.joining(", "));
    }

    /**
     * Returns an HTML table-row fragment for each item, suitable for embedding
     * in an email template. Includes product name, quantity, unit price with
     * discount annotation, and line total. Example output:
     * <pre>
     * &lt;tr&gt;&lt;td&gt;iPhone 14&lt;/td&gt;&lt;td&gt;2&lt;/td&gt;&lt;td&gt;LKR 19000 (5% off)&lt;/td&gt;&lt;td&gt;LKR 38000&lt;/td&gt;&lt;/tr&gt;
     * </pre>
     */
    public static String buildItemSummaryHtml(Order order) {
        return order.getItems().stream()
                .map(item -> {
                    String name = item.getProduct().getName();
                    int qty = item.getQuantity();
                    BigDecimal unitPrice = item.getUnitPrice();
                    BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(qty));
                    String discountInfo = "";
                    if (item.getDiscountType() != null
                            && item.getDiscountValue() != null
                            && item.getDiscountValue().compareTo(BigDecimal.ZERO) > 0) {
                        String pct = "PERCENTAGE".equalsIgnoreCase(item.getDiscountType())
                                ? item.getDiscountValue().toPlainString() + "% off"
                                : "LKR " + item.getDiscountValue().toPlainString() + " off";
                        discountInfo = " <span style=\"color:#059669;\">(" + pct + ")</span>";
                    }
                    return "<tr style=\"border-bottom:1px solid #e5e7eb;\">"
                            + "<td style=\"padding:8px 12px;\">" + name + "</td>"
                            + "<td style=\"padding:8px 12px;text-align:center;\">" + qty + "</td>"
                            + "<td style=\"padding:8px 12px;text-align:right;\">LKR " + NumberUtils.formatAmount(unitPrice)
                            + discountInfo + "</td>"
                            + "<td style=\"padding:8px 12px;text-align:right;\">LKR " + NumberUtils.formatAmount(lineTotal)
                            + "</td></tr>";
                })
                .collect(Collectors.joining("\n"));
    }

    /**
     * Like {@link #buildItemSummary(Order)} but appends the per-item discount
     * description and line-level savings when the order item has a discount.
     * Example: "iPhone 14 (x2) — 5% off (saves LKR 1,000.00)"
     */
    public static String buildItemSummaryWithDiscount(Order order) {
        return order.getItems().stream()
                .map(item -> {
                    String base = item.getProduct().getName() + " (x" + item.getQuantity() + ")";
                    BigDecimal discountValue = item.getDiscountValue();
                    if (item.getDiscountType() != null
                            && discountValue != null
                            && discountValue.compareTo(BigDecimal.ZERO) > 0
                            && item.getOriginalUnitPrice() != null) {
                        BigDecimal lineSavings = item.getOriginalUnitPrice()
                                .subtract(item.getUnitPrice())
                                .multiply(BigDecimal.valueOf(item.getQuantity()));
                        String pct = "PERCENTAGE".equalsIgnoreCase(item.getDiscountType())
                                ? discountValue.toPlainString() + "% off"
                                : "LKR " + discountValue.toPlainString() + " off";
                        base += " — " + pct + " (saves LKR " + NumberUtils.formatAmount(lineSavings) + ")";
                    }
                    return base;
                })
                .collect(Collectors.joining("\n"));
    }

    /**
     * Bullet-point item summary for SMS, with discount info per item.
     * Example:
     * <pre>
     * \u2022 iPhone 14 (x2) \u2014 5% off (saves LKR 1,000.00)
     * \u2022 USB Cable (x1) \u2014 LKR 500.00 off (saves LKR 500.00)
     * </pre>
     */
    public static String buildItemSummaryBullet(Order order) {
        return order.getItems().stream()
                .map(item -> {
                    String base = item.getProduct().getName() + " (x" + item.getQuantity() + ")";
                    BigDecimal discountValue = item.getDiscountValue();
                    if (item.getDiscountType() != null
                            && discountValue != null
                            && discountValue.compareTo(BigDecimal.ZERO) > 0
                            && item.getOriginalUnitPrice() != null) {
                        BigDecimal lineSavings = item.getOriginalUnitPrice()
                                .subtract(item.getUnitPrice())
                                .multiply(BigDecimal.valueOf(item.getQuantity()));
                        String pct = "PERCENTAGE".equalsIgnoreCase(item.getDiscountType())
                                ? discountValue.toPlainString() + "% off"
                                : "LKR " + discountValue.toPlainString() + " off";
                        base += " — " + pct + " (saves LKR " + NumberUtils.formatAmount(lineSavings) + ")";
                    }
                    return "\u2022 " + base;
                })
                .collect(Collectors.joining("\n"));
    }

    /**
     * Sum of original unit_price × quantity across all order items. Uses the
     * stored originalUnitPrice when present, falls back to unitPrice.
     */
    public static BigDecimal totalOriginalAmount(Order order) {
        if (order == null || order.getItems() == null) {
            return BigDecimal.ZERO;
        }
        return order.getItems().stream()
                .map(i -> {
                    BigDecimal orig = i.getOriginalUnitPrice() != null
                            ? i.getOriginalUnitPrice()
                            : i.getUnitPrice();
                    return orig.multiply(BigDecimal.valueOf(i.getQuantity()));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Total savings (original - final) for the order, or ZERO when the order
     * has no discount. Equivalent to {@code totalOriginalAmount(order) -
     * order.getTotalAmount()}, floored at zero.
     */
    public static BigDecimal totalSavings(Order order) {
        BigDecimal original = totalOriginalAmount(order);
        BigDecimal finalAmount = order.getTotalAmount() != null
                ? order.getTotalAmount()
                : BigDecimal.ZERO;
        BigDecimal savings = original.subtract(finalAmount);
        return savings.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : savings;
    }

    public static String customerDisplayName(Customer customer) {
        return customer.getFirstName() + " " + customer.getLastName();
    }
}
