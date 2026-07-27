package com.ebee.customer.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class CustomerResponse {

    private UUID id;
    private String customerCode;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String email;
    private Integer totalOrders;
    private BigDecimal creditLimit;
    private BigDecimal outstandingBalance;
    private BigDecimal advanceBalance;
    private AddressResponse address;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
