package com.ebee.order.util;

import com.ebee.customer.entity.Customer;
import com.ebee.order.entity.Order;
import com.ebee.order.entity.OrderItem;

import java.util.stream.Collectors;

public final class OrderItemSummaryBuilder {

    private OrderItemSummaryBuilder() {}

    public static String buildItemSummary(Order order) {
        return order.getItems().stream()
                .map(item -> item.getProduct().getName() + " (x" + item.getQuantity() + ")")
                .collect(Collectors.joining(", "));
    }

    public static String customerDisplayName(Customer customer) {
        return customer.getFirstName() + " " + customer.getLastName();
    }
}
