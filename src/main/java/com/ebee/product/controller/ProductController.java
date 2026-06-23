package com.ebee.product.controller;

import com.ebee.common.enums.DiscountType;
import com.ebee.common.response.ApiResponse;
import com.ebee.common.response.PageResponse;
import com.ebee.product.dto.ProductRequest;
import com.ebee.product.dto.ProductResponse;
import com.ebee.product.service.ProductService;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.ebee.common.util.PaginationUtils;
import com.ebee.common.util.SortUtils;
import com.ebee.common.util.SortableFields;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

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
            @RequestParam(value = "images", required = false) MultipartFile[] images,
            @RequestParam(value = "discountType", required = false) String discountType,
            @RequestParam(value = "discountValue", required = false) BigDecimal discountValue,
            @RequestParam(value = "maxDiscountType", required = false) String maxDiscountType,
            @RequestParam(value = "maxDiscountValue", required = false) BigDecimal maxDiscountValue,
            @RequestParam(value = "brand", required = false) String brand,
            @RequestParam(value = "tags", required = false) String tags) {

        ProductRequest request = new ProductRequest();
        request.setName(name);
        request.setItemCode(itemCode);
        request.setDescription(description);
        request.setCategory(category);
        request.setPrice(price);
        request.setCostPrice(costPrice);
        request.setDiscountType(DiscountType.fromString(discountType));
        request.setDiscountValue(discountValue);
        request.setMaxDiscountType(DiscountType.fromString(maxDiscountType));
        request.setMaxDiscountValue(maxDiscountValue);
        request.setBrand(brand);
        request.setTags(tags);
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
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String stock) {

        Pageable pageable = PageRequest.of(
                PaginationUtils.safePage(page),
                PaginationUtils.safeSize(size),
                SortUtils.parse(sort, SortableFields.AllowedFor.PRODUCT));

        Page<ProductResponse> productsPage = search != null && !search.trim().isEmpty()
                ? productService.searchProducts(search.trim(), pageable, category, stock)
                : productService.getAllProductsPaginated(pageable, category, stock);

        PageResponse<ProductResponse> response = PageResponse.from(productsPage);

        return ResponseEntity.ok(ApiResponse.success("Products retrieved successfully", response));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> searchProducts(@RequestParam("q") String query) {
        List<ProductResponse> response = productService.searchProducts(query.trim());
        return ResponseEntity.ok(ApiResponse.success("Products retrieved successfully", response));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getActiveProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String stock) {

        Pageable pageable = PageRequest.of(
                PaginationUtils.safePage(page),
                PaginationUtils.safeSize(size),
                SortUtils.parse(sort, SortableFields.AllowedFor.PRODUCT));

        Page<ProductResponse> productsPage = productService.getActiveProductsPaginated(pageable, category, stock);

        PageResponse<ProductResponse> response = PageResponse.from(productsPage);

        return ResponseEntity.ok(ApiResponse.success("Active products retrieved successfully", response));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<java.util.Map<String, Long>>> getProductStats() {
        return ResponseEntity.ok(ApiResponse.success("Product stats retrieved successfully", productService.getProductStats()));
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
            @RequestParam(value = "images", required = false) MultipartFile[] images,
            @RequestParam(value = "discountType", required = false) String discountType,
            @RequestParam(value = "discountValue", required = false) BigDecimal discountValue,
            @RequestParam(value = "maxDiscountType", required = false) String maxDiscountType,
            @RequestParam(value = "maxDiscountValue", required = false) BigDecimal maxDiscountValue,
            @RequestParam(value = "brand", required = false) String brand,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "removeImageUrls", required = false) List<String> removeImageUrls) {

        ProductRequest request = new ProductRequest();
        request.setName(name);
        request.setItemCode(itemCode);
        request.setDescription(description);
        request.setCategory(category);
        request.setPrice(price);
        request.setCostPrice(costPrice);
        request.setDiscountType(DiscountType.fromString(discountType));
        request.setDiscountValue(discountValue);
        request.setMaxDiscountType(DiscountType.fromString(maxDiscountType));
        request.setMaxDiscountValue(maxDiscountValue);
        request.setBrand(brand);
        request.setTags(tags);
        request.setRemoveImageUrls(removeImageUrls);
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

    @DeleteMapping("/{productId}/images/{imageId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProductImage(
            @PathVariable UUID productId,
            @PathVariable UUID imageId) {

        productService.deleteProductImage(productId, imageId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> activateProduct(@PathVariable UUID id) {
        ProductResponse response = productService.activateProduct(id);
        return ResponseEntity.ok(ApiResponse.success("Product activated successfully", response));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateProduct(@PathVariable UUID id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success("Product deactivated successfully", null));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProduct(@PathVariable UUID id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/label")
    public ResponseEntity<Resource> downloadLabel(@PathVariable UUID id) {
        byte[] pdf = productService.generateProductLabelPdf(id);
        ByteArrayResource resource = new ByteArrayResource(pdf);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=product-label-" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(resource);
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
