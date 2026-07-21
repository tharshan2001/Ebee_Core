package com.ebee.common.util;

import java.util.Map;
import java.util.Set;

/**
 * Allow-list of sortable fields per entity. Callers can request a sort
 * via {@code ?sort=fieldName,asc} or {@code ?sort=fieldName,desc}.
 * Without a guard, a caller can sort by any column -- including
 * non-indexed ones, which is a DoS vector on large tables.
 *
 * The default is "createdAt,desc" which is the same default most
 * controllers already use.
 */
public final class SortableFields {

    private SortableFields() {}

    public static final String DEFAULT = "createdAt,desc";

    public static final Set<String> ORDER = Set.of(
            "createdAt", "updatedAt", "orderNumber", "totalAmount",
            "status", "deliveryStatus"
    );

    public static final Set<String> PRODUCT = Set.of(
            "createdAt", "updatedAt", "name", "itemCode",
            "price", "costPrice"
    );

    public static final Set<String> CUSTOMER = Set.of(
            "createdAt", "updatedAt", "firstName", "lastName",
            "phoneNumber", "customerCode", "totalOrders"
    );

    public static final Set<String> AGENT = Set.of(
            "createdAt", "updatedAt", "firstName", "lastName",
            "agentCode", "commissionRate", "isActive"
    );

    public static final Set<String> COMMISSION = Set.of(
            "createdAt", "updatedAt", "calculatedAt", "approvedAt",
            "paidAt", "status", "orderAmount", "commissionAmount"
    );

    public static final Set<String> DRIVER_DELIVERY = Set.of(
            "createdAt", "updatedAt", "orderNumber", "deliveryStatus",
            "scheduledDate", "deliveredAt"
    );

    /**
     * Parse a {@code sort} query parameter (e.g. {@code "createdAt,desc"})
     * and validate the field against the allow-list. Returns a normalised
     * {@code "field,dir"} string. Throws {@link IllegalArgumentException}
     * if the field is not in the allow-list.
     */
    public static String validate(String sort, Set<String> allowed) {
        if (sort == null || sort.isBlank()) {
            return DEFAULT;
        }
        String direction = sort.endsWith(",asc") ? ",asc" : ",desc";
        String field = sort.endsWith(",asc")
                ? sort.substring(0, sort.length() - 4)
                : sort.endsWith(",desc")
                    ? sort.substring(0, sort.length() - 5)
                    : sort;
        if (!allowed.contains(field)) {
            throw new IllegalArgumentException(
                    "Invalid sort field '" + field + "'. Allowed: " + allowed);
        }
        return field + direction;
    }

    /** Convenience overload for the common case: validate against a single entity. */
    public static String validate(String sort, AllowedFor allowedFor) {
        return validate(sort, allowList(allowedFor));
    }

    public enum AllowedFor { ORDER, PRODUCT, CUSTOMER, AGENT, COMMISSION, DRIVER_DELIVERY }

    public static Set<String> allowList(AllowedFor which) {
        return switch (which) {
            case ORDER -> ORDER;
            case PRODUCT -> PRODUCT;
            case CUSTOMER -> CUSTOMER;
            case AGENT -> AGENT;
            case COMMISSION -> COMMISSION;
            case DRIVER_DELIVERY -> DRIVER_DELIVERY;
        };
    }
}
