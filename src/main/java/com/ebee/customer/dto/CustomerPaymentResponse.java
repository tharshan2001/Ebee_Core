package com.ebee.customer.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CustomerPaymentResponse(
        UUID customerId,
        String customerName,
        BigDecimal amountPaid,
        int ordersSettled,
        BigDecimal customerRemainingBalance
) {}
