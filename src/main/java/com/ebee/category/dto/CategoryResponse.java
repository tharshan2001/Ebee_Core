package com.ebee.category.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class CategoryResponse {

    private UUID id;
    private String name;
    private boolean active;
}
