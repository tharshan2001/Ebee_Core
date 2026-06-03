package com.ebee.customer.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class AddressResponse {

    private UUID id;
    private String street;
    private String city;
    private String district;
    private String postalCode;
}
