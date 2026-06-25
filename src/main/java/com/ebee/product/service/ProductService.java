package com.ebee.product.service;

import com.ebee.product.dto.ProductRequest;
import com.ebee.product.dto.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ProductService {

    ProductResponse createProduct(ProductRequest request);

    ProductResponse getProductById(UUID id);

    ProductResponse getProductByItemCode(String itemCode);

    List<ProductResponse> getAllProducts();

    List<ProductResponse> getProductsByCategory(String category);

    List<ProductResponse> getActiveProducts();

    Page<ProductResponse> getAllProductsPaginated(Pageable pageable, String category, String stock);

    Page<ProductResponse> getActiveProductsPaginated(Pageable pageable, String category, String stock);

    Map<String, Long> getProductStats();

    Page<ProductResponse> searchProducts(String search, Pageable pageable, String category, String stock);

    List<ProductResponse> searchProducts(String search);

    ProductResponse updateProduct(UUID id, ProductRequest request);

    ProductResponse addImagesToProduct(UUID id, List<MultipartFile> images);

    ProductResponse activateProduct(UUID id);

    void deleteProduct(UUID id);

    void deleteProductImage(UUID productId, UUID imageId);

    byte[] generateProductLabelPdf(UUID id);

    Page<ProductResponse> getFeaturedProducts(Pageable pageable);

    Page<ProductResponse> getDealProducts(Pageable pageable);

    Page<ProductResponse> getBestSellerProducts(Pageable pageable);

    Page<ProductResponse> getNewArrivals(Pageable pageable);
}
