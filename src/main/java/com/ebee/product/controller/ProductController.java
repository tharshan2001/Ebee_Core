package com.ebee.product.controller;

import com.ebee.common.response.ApiResponse;
import com.ebee.product.dto.ProductRequest;
import com.ebee.product.dto.ProductResponse;
import com.ebee.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @RequestParam("name") String name,
            @RequestParam("sku") String sku,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("category") String category,
            @RequestParam("price") BigDecimal price,
            @RequestParam("costPrice") BigDecimal costPrice,
            @RequestParam("quantityInStock") Integer quantityInStock,
            @RequestParam(value = "reorderLevel", required = false) Integer reorderLevel,
            @RequestParam(value = "images", required = false) MultipartFile[] images) throws IOException {

        ProductRequest request = new ProductRequest();
        request.setName(name);
        request.setSku(sku);
        request.setDescription(description);
        request.setCategory(category);
        request.setPrice(price);
        request.setCostPrice(costPrice);
        request.setQuantityInStock(quantityInStock);
        request.setReorderLevel(reorderLevel);
        if (images != null) {
            request.setImages(Arrays.asList(images));
        }

        ProductResponse response = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product created successfully", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(@PathVariable UUID id) {
        ProductResponse response = productService.getProductById(id);
        return ResponseEntity.ok(ApiResponse.success("Product retrieved successfully", response));
    }

    @GetMapping("/sku/{sku}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductBySku(@PathVariable String sku) {
        ProductResponse response = productService.getProductBySku(sku);
        return ResponseEntity.ok(ApiResponse.success("Product retrieved successfully", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getAllProducts() {
        List<ProductResponse> response = productService.getAllProducts();
        return ResponseEntity.ok(ApiResponse.success("Products retrieved successfully", response));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getActiveProducts() {
        List<ProductResponse> response = productService.getActiveProducts();
        return ResponseEntity.ok(ApiResponse.success("Active products retrieved successfully", response));
    }

    @PatchMapping(value = "/{id}", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable UUID id,
            @RequestParam("name") String name,
            @RequestParam("sku") String sku,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("category") String category,
            @RequestParam("price") BigDecimal price,
            @RequestParam("costPrice") BigDecimal costPrice,
            @RequestParam("quantityInStock") Integer quantityInStock,
            @RequestParam(value = "reorderLevel", required = false) Integer reorderLevel,
            @RequestParam(value = "images", required = false) MultipartFile[] images) throws IOException {

        ProductRequest request = new ProductRequest();
        request.setName(name);
        request.setSku(sku);
        request.setDescription(description);
        request.setCategory(category);
        request.setPrice(price);
        request.setCostPrice(costPrice);
        request.setQuantityInStock(quantityInStock);
        request.setReorderLevel(reorderLevel);
        if (images != null) {
            request.setImages(Arrays.asList(images));
        }

        ProductResponse response = productService.updateProduct(id, request);
        return ResponseEntity.ok(ApiResponse.success("Product updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable UUID id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success("Product deleted successfully"));
    }
}
