package com.ebee.product.controller;

import com.ebee.common.response.ApiResponse;
import com.ebee.product.dto.ProductRequest;
import com.ebee.product.dto.ProductResponse;
import com.ebee.product.service.ProductService;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final Validator validator;

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @RequestParam("name") String name,
            @RequestParam("itemCode") String itemCode,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("category") String category,
            @RequestParam("price") BigDecimal price,
            @RequestParam("costPrice") BigDecimal costPrice,
            @RequestParam(value = "images", required = false) MultipartFile[] images) {

        ProductRequest request = new ProductRequest();
        request.setName(name);
        request.setItemCode(itemCode);
        request.setDescription(description);
        request.setCategory(category);
        request.setPrice(price);
        request.setCostPrice(costPrice);
        if (images != null) {
            request.setImages(Arrays.asList(images));
        }
        validate(request);

        ProductResponse response = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product created successfully", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(@PathVariable UUID id) {
        ProductResponse response = productService.getProductById(id);
        return ResponseEntity.ok(ApiResponse.success("Product retrieved successfully", response));
    }

    @GetMapping("/item-code/{itemCode}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductByItemCode(@PathVariable String itemCode) {
        ProductResponse response = productService.getProductByItemCode(itemCode);
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
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "itemCode", required = false) String itemCode,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "price", required = false) BigDecimal price,
            @RequestParam(value = "costPrice", required = false) BigDecimal costPrice,
            @RequestParam(value = "images", required = false) MultipartFile[] images) {

        ProductRequest request = new ProductRequest();
        request.setName(name);
        request.setItemCode(itemCode);
        request.setDescription(description);
        request.setCategory(category);
        request.setPrice(price);
        request.setCostPrice(costPrice);
        if (images != null) {
            request.setImages(Arrays.asList(images));
        }
        validate(request);

        ProductResponse response = productService.updateProduct(id, request);
        return ResponseEntity.ok(ApiResponse.success("Product updated successfully", response));
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getProductsByCategory(
            @PathVariable String category) {
        List<ProductResponse> response = productService.getProductsByCategory(category);
        return ResponseEntity.ok(ApiResponse.success("Products retrieved successfully", response));
    }

    @PostMapping("/{id}/images")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> uploadProductImages(
            @PathVariable UUID id,
            @RequestParam("images") MultipartFile[] images) {

        ProductResponse response = productService.addImagesToProduct(id, Arrays.asList(images));
        return ResponseEntity.ok(ApiResponse.success("Images uploaded successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable UUID id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success("Product deleted successfully"));
    }

    private void validate(ProductRequest request) {
        Set<jakarta.validation.ConstraintViolation<ProductRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining("; "));
            throw new IllegalArgumentException(message);
        }
    }
}
