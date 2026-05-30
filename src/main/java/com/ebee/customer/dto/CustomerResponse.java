package com.ebee.customer.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class CustomerResponse {

    private UUID id;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String email;
    private Integer totalOrders;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
