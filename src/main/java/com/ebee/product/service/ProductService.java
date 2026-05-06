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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final ProductImageRepository productImageRepository;
    private final S3Service s3Service;

    @Transactional
    public ProductResponse createProduct(ProductRequest request) throws IOException {
        if (productRepository.existsBySku(request.getSku())) {
            throw new IllegalArgumentException("SKU already exists: " + request.getSku());
        }

        Product product = productMapper.toEntity(request);
        Product savedProduct = productRepository.save(product);

        // Handle image uploads to S3 (up to 10 images)
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            int sortOrder = 0;
            for (MultipartFile image : request.getImages()) {
                if (sortOrder >= 10) break; // Max 10 images
                if (image != null && !image.isEmpty()) {
                    String imageUrl = s3Service.uploadFile(image, "products/" + savedProduct.getId());
                    ProductImage productImage = ProductImage.builder()
                            .product(savedProduct)
                            .imageUrl(imageUrl)
                            .imageName(image.getOriginalFilename())
                            .imageType(image.getContentType())
                            .sortOrder(sortOrder++)
                            .build();
                    productImageRepository.save(productImage);
                }
            }
        }

        return productMapper.toResponse(savedProduct);
    }

    public ProductResponse getProductById(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        ProductResponse response = productMapper.toResponse(product);
        // Add image URLs to response
        List<ProductImage> images = productImageRepository.findByProductIdOrderBySortOrder(id);
        List<String> imageUrls = images.stream()
                .map(ProductImage::getImageUrl)
                .collect(java.util.stream.Collectors.toList());
        response.setImageUrls(imageUrls);
        return response;
    }

    public ProductResponse getProductBySku(String sku) {
        Product product = productRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "sku", sku));
        ProductResponse response = productMapper.toResponse(product);
        // Add image URLs to response
        List<ProductImage> images = productImageRepository.findByProductIdOrderBySortOrder(product.getId());
        List<String> imageUrls = images.stream()
                .map(ProductImage::getImageUrl)
                .collect(java.util.stream.Collectors.toList());
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
                            .collect(java.util.stream.Collectors.toList());
                    response.setImageUrls(imageUrls);
                    return response;
                })
                .collect(java.util.stream.Collectors.toList());
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
                            .collect(java.util.stream.Collectors.toList());
                    response.setImageUrls(imageUrls);
                    return response;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    @Transactional
    public ProductResponse updateProduct(UUID id, ProductRequest request) throws IOException {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        if (!product.getSku().equals(request.getSku()) &&
            productRepository.existsBySkuAndIdNot(request.getSku(), id)) {
            throw new IllegalArgumentException("SKU already exists: " + request.getSku());
        }

        product.setName(request.getName());
        product.setSku(request.getSku());
        product.setDescription(request.getDescription());
        product.setCategory(request.getCategory());
        product.setPrice(request.getPrice());
        product.setCostPrice(request.getCostPrice());
        product.setQuantityInStock(request.getQuantityInStock());
        product.setReorderLevel(request.getReorderLevel());

        // Handle new image uploads (append to existing, max 10 total)
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            List<ProductImage> existingImages = productImageRepository.findByProductIdOrderBySortOrder(id);
            int sortOrder = existingImages.size();

            for (MultipartFile image : request.getImages()) {
                if (sortOrder >= 10) break; // Max 10 images total
                if (image != null && !image.isEmpty()) {
                    String imageUrl = s3Service.uploadFile(image, "products/" + id);
                    ProductImage productImage = ProductImage.builder()
                            .product(product)
                            .imageUrl(imageUrl)
                            .imageName(image.getOriginalFilename())
                            .imageType(image.getContentType())
                            .sortOrder(sortOrder++)
                            .build();
                    productImageRepository.save(productImage);
                }
            }
        }

        Product updatedProduct = productRepository.save(product);
        return productMapper.toResponse(updatedProduct);
    }

    @Transactional
    public void deleteProduct(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        // Delete all images from S3
        List<ProductImage> images = productImageRepository.findByProductIdOrderBySortOrder(id);
        for (ProductImage image : images) {
            s3Service.deleteFile(image.getImageUrl());
        }
        productImageRepository.deleteByProductId(id);

        product.setIsActive(false);
        productRepository.save(product);
    }

}
