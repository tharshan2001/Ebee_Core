package com.ebee.product.service;

import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.service.S3Service;
import com.ebee.product.dto.ProductRequest;
import com.ebee.product.dto.ProductResponse;
import com.ebee.product.entity.Product;
import com.ebee.product.entity.ProductImage;
import com.ebee.product.mapper.ProductMapper;
import com.ebee.product.repository.ProductImageRepository;
import com.ebee.product.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final ProductImageRepository productImageRepository;
    private final S3Service s3Service;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public ProductResponse createProduct(ProductRequest request) throws IOException {
        if (productRepository.existsBySku(request.getSku())) {
            throw new IllegalArgumentException("SKU already exists: " + request.getSku());
        }

        Product product = productMapper.toEntity(request);
        Product savedProduct = productRepository.save(product);
        productRepository.flush();
        UUID productId = savedProduct.getId();
        log.info("Product saved with ID: {}", productId);

        if (request.getImages() != null && !request.getImages().isEmpty()) {
            int sortOrder = 0;
            for (MultipartFile image : request.getImages()) {
                if (sortOrder >= 10) break;
                if (image != null && !image.isEmpty()) {
                    String imageUrl = s3Service.uploadFile(image, "products/" + productId);
                    log.info("Uploaded image to: {}", imageUrl);

                    jdbcTemplate.update(
                            "INSERT INTO product_images (id, product_id, image_url, image_name, image_type, sort_order, created_at) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                            UUID.randomUUID(), productId, imageUrl, image.getOriginalFilename(), image.getContentType(), sortOrder++
                    );
                    log.info("Image inserted for product_id={}", productId);
                }
            }
        }

        return productMapper.toResponse(savedProduct);
    }

    public ProductResponse getProductById(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        ProductResponse response = productMapper.toResponse(product);
        List<ProductImage> images = productImageRepository.findByProductIdOrderBySortOrder(id);
        List<String> imageUrls = images.stream()
                .map(ProductImage::getImageUrl)
                .toList();
        response.setImageUrls(imageUrls);
        return response;
    }

    public ProductResponse getProductBySku(String sku) {
        Product product = productRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "sku", sku));
        ProductResponse response = productMapper.toResponse(product);
        List<ProductImage> images = productImageRepository.findByProductIdOrderBySortOrder(product.getId());
        List<String> imageUrls = images.stream()
                .map(ProductImage::getImageUrl)
                .toList();
        response.setImageUrls(imageUrls);
        return response;
    }

    public List<ProductResponse> getAllProducts() {
        List<Product> products = productRepository.findAll();
        return products.stream()
                .map(p -> {
                    ProductResponse response = productMapper.toResponse(p);
                    List<ProductImage> images = productImageRepository.findByProductIdOrderBySortOrder(p.getId());
                    List<String> imageUrls = images.stream()
                            .map(ProductImage::getImageUrl)
                            .toList();
                    response.setImageUrls(imageUrls);
                    return response;
                })
                .toList();
    }

    public List<ProductResponse> getActiveProducts() {
        List<Product> products = productRepository.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .toList();
        return products.stream()
                .map(p -> {
                    ProductResponse response = productMapper.toResponse(p);
                    List<ProductImage> images = productImageRepository.findByProductIdOrderBySortOrder(p.getId());
                    List<String> imageUrls = images.stream()
                            .map(ProductImage::getImageUrl)
                            .toList();
                    response.setImageUrls(imageUrls);
                    return response;
                })
                .toList();
    }

    @Transactional
    public ProductResponse updateProduct(UUID id, ProductRequest request) throws IOException {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        if (request.getName() != null) {
            product.setName(request.getName());
        }
        if (request.getSku() != null) {
            if (!product.getSku().equals(request.getSku()) &&
                productRepository.existsBySkuAndIdNot(request.getSku(), id)) {
                throw new IllegalArgumentException("SKU already exists: " + request.getSku());
            }
            product.setSku(request.getSku());
        }
        if (request.getDescription() != null) {
            product.setDescription(request.getDescription());
        }
        if (request.getCategory() != null) {
            product.setCategory(request.getCategory());
        }
        if (request.getPrice() != null) {
            product.setPrice(request.getPrice());
        }
        if (request.getCostPrice() != null) {
            product.setCostPrice(request.getCostPrice());
        }
        if (request.getQuantityInStock() != null) {
            product.setQuantityInStock(request.getQuantityInStock());
        }
        if (request.getReorderLevel() != null) {
            product.setReorderLevel(request.getReorderLevel());
        }

        if (request.getImages() != null && !request.getImages().isEmpty()) {
            List<ProductImage> existingImages = productImageRepository.findByProductIdOrderBySortOrder(id);
            int sortOrder = existingImages.size();

            for (MultipartFile image : request.getImages()) {
                if (sortOrder >= 10) break;
                if (image != null && !image.isEmpty()) {
                    String imageUrl = s3Service.uploadFile(image, "products/" + id);

                    jdbcTemplate.update(
                            "INSERT INTO product_images (id, product_id, image_url, image_name, image_type, sort_order, created_at) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                            UUID.randomUUID(), id, imageUrl, image.getOriginalFilename(), image.getContentType(), sortOrder++
                    );
                    log.info("Image inserted for product_id={}", id);
                }
            }
        }

        Product updatedProduct = productRepository.save(product);
        return productMapper.toResponse(updatedProduct);
    }

    @Transactional
    public ProductResponse addImagesToProduct(UUID id, List<MultipartFile> images) throws IOException {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        List<ProductImage> existingImages = productImageRepository.findByProductIdOrderBySortOrder(id);
        int sortOrder = existingImages.size();

        if (sortOrder >= 10) {
            throw new IllegalStateException("Product already has maximum of 10 images");
        }

        for (MultipartFile image : images) {
            if (sortOrder >= 10) break;
            if (image != null && !image.isEmpty()) {
                String imageUrl = s3Service.uploadFile(image, "products/" + id);

                jdbcTemplate.update(
                        "INSERT INTO product_images (id, product_id, image_url, image_name, image_type, sort_order, created_at) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                        UUID.randomUUID(), id, imageUrl, image.getOriginalFilename(), image.getContentType(), sortOrder++
                );
            }
        }

        ProductResponse response = productMapper.toResponse(product);
        List<ProductImage> updatedImages = productImageRepository.findByProductIdOrderBySortOrder(id);
        List<String> imageUrls = updatedImages.stream()
                .map(ProductImage::getImageUrl)
                .toList();
        response.setImageUrls(imageUrls);
        return response;
    }

    @Transactional
    public void deleteProduct(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        List<ProductImage> images = productImageRepository.findByProductIdOrderBySortOrder(id);
        for (ProductImage image : images) {
            s3Service.deleteFile(image.getImageUrl());
        }
        productImageRepository.deleteByProductId(id);

        product.setIsActive(false);
        productRepository.save(product);
    }
}
