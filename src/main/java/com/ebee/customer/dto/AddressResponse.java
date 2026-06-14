package com.ebee.customer.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.UUID;

@Data
public class AddressResponse {

    @JsonIgnore
    private UUID id;
    private String street;
    private String city;
    private String district;
    private String postalCode;
}
