package com.ebee.product.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class ProductImageResponse {

    private UUID id;
    private String imageUrl;
    private String imageName;
    private String imageType;
    private Integer sortOrder;
}
