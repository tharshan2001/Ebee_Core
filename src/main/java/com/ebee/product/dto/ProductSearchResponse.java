package com.ebee.product.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchResponse {
    private List<ProductResponse> products;
    private boolean hasMore;
    private int totalPages;
    private long totalElements;
}
