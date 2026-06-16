package com.ebee.order.util;

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
                        base += " — " + pct + " (saves LKR " + lineSavings.toPlainString() + ")";
                    }
                    return base;
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
