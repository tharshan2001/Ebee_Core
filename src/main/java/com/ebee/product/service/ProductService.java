package com.ebee.product.service;

import com.ebee.category.service.CategoryService;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.service.S3Service;
import com.ebee.inventory.mapper.InventoryMapper;
import com.ebee.inventory.service.InventoryService;
import com.ebee.product.dto.ProductImageResponse;
import com.ebee.product.dto.ProductRequest;
import com.ebee.product.dto.ProductResponse;
import com.ebee.product.entity.Product;
import com.ebee.product.entity.ProductImage;
import com.ebee.product.mapper.ProductMapper;
import com.ebee.product.repository.ProductImageRepository;
import com.ebee.product.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final ProductImageRepository productImageRepository;
    private final S3Service s3Service;
    private final JdbcTemplate jdbcTemplate;
    private final InventoryService inventoryService;
    private final InventoryMapper inventoryMapper;
    private final EntityManager entityManager;
    private final CategoryService categoryService;

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        if (productRepository.existsByItemCode(request.getItemCode())) {
            throw new IllegalArgumentException("Item code already exists: " + request.getItemCode());
        }

        categoryService.findByNameOrThrow(request.getCategory());
        validateDiscount(request);

        Product product = productMapper.toEntity(request);
        Product savedProduct = productRepository.save(product);
        productRepository.flush();
        UUID productId = savedProduct.getId();
        log.info("Product saved with ID: {}", productId);

        inventoryService.createInventory(productId, 0, null);
        log.info("Inventory created for product: {}", productId);

        if (request.getImages() != null && !request.getImages().isEmpty()) {
            List<MultipartFile> imageList = request.getImages().stream()
                    .filter(img -> img != null && !img.isEmpty())
                    .limit(10)
                    .toList();

            int sortOrder = 0;
            for (MultipartFile image : imageList) {
                String imageUrl = s3Service.uploadFile(image, "products/" + productId);
                if (imageUrl != null) {
                    jdbcTemplate.update(
                            "INSERT INTO product_images (id, product_id, image_url, image_name, image_type, sort_order, created_at) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                            UUID.randomUUID(), productId, imageUrl, image.getOriginalFilename(), image.getContentType(), sortOrder++
                    );
                    log.info("Image inserted for product_id={}", productId);
                }
            }
        }

        return getProductById(productId);
    }

    public ProductResponse getProductById(UUID id) {
        Product product = productRepository.findByIdWithRelations(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        return toResponseWithRelations(product);
    }

    public ProductResponse getProductByItemCode(String itemCode) {
        Product product = productRepository.findByItemCodeWithRelations(itemCode)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "itemCode", itemCode));
        return toResponseWithRelations(product);
    }

    public List<ProductResponse> getAllProducts() {
        List<Product> products = productRepository.findAllWithInventoryAndImages();
        return buildResponsesWithBatchImages(products);
    }

    public List<ProductResponse> getProductsByCategory(String category) {
        List<Product> products = productRepository.findByCategoryIgnoreCase(category);
        return buildResponsesWithBatchImages(products);
    }

    public List<ProductResponse> getActiveProducts() {
        List<Product> products = productRepository.findAllWithInventoryAndImages().stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .toList();
        return buildResponsesWithBatchImages(products);
    }

    public Page<ProductResponse> getAllProductsPaginated(Pageable pageable) {
        return productRepository.findAllWithInventoryAndImages(pageable)
                .map(this::toResponseWithRelations);
    }

    public Page<ProductResponse> getActiveProductsPaginated(Pageable pageable) {
        return productRepository.findAllActiveWithInventoryAndImages(pageable)
                .map(this::toResponseWithRelations);
    }

    public Page<ProductResponse> searchProducts(String search, Pageable pageable) {
        return productRepository.findBySearchTerm(search, pageable)
                .map(this::toResponseWithRelations);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> searchProducts(String search) {
        return productRepository.findBySearchTerm(search).stream()
                .map(this::toResponseWithRelations)
                .toList();
    }

    private List<ProductResponse> buildResponsesWithBatchImages(List<Product> products) {
        if (products.isEmpty()) return List.of();

        List<UUID> productIds = products.stream().map(Product::getId).toList();
        Map<UUID, List<String>> imageUrlsByProductId = productImageRepository
                .findByProductIdsWithProductOrderBySortOrder(productIds)
                .stream()
                .collect(Collectors.groupingBy(
                        pi -> pi.getProduct().getId(),
                        Collectors.mapping(ProductImage::getImageUrl, Collectors.toList())
                ));

        return products.stream()
                .map(p -> {
                    ProductResponse response = productMapper.toResponse(p);
                    response.setImageUrls(imageUrlsByProductId.getOrDefault(p.getId(), List.of()));
                    List<ProductImageResponse> images = productImageRepository
                            .findByProductIdOrderBySortOrder(p.getId())
                            .stream()
                            .map(this::toProductImageResponse)
                            .toList();
                    response.setImages(images);
                    if (p.getInventory() != null) {
                        response.setInventory(inventoryMapper.toResponse(p.getInventory()));
                    }
                    return response;
                })
                .toList();
    }

    @Transactional
    public ProductResponse updateProduct(UUID id, ProductRequest request) {
        Product product = productRepository.findByIdWithRelations(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        if (request.getName() != null) {
            product.setName(request.getName());
        }
        if (request.getItemCode() != null) {
            if (!product.getItemCode().equals(request.getItemCode()) &&
                productRepository.existsByItemCodeAndIdNot(request.getItemCode(), id)) {
                throw new IllegalArgumentException("Item code already exists: " + request.getItemCode());
            }
            product.setItemCode(request.getItemCode());
        }
        if (request.getDescription() != null) {
            product.setDescription(request.getDescription());
        }
        if (request.getCategory() != null && !request.getCategory().equals(product.getCategory())) {
            categoryService.findByNameOrThrow(request.getCategory());
            product.setCategory(request.getCategory());
        }
        if (request.getPrice() != null) {
            product.setPrice(request.getPrice());
        }
        if (request.getCostPrice() != null) {
            product.setCostPrice(request.getCostPrice());
        }
        validateDiscount(request);
        if (request.getDiscountType() != null) {
            product.setDiscountType(request.getDiscountType());
        }
        if (request.getDiscountValue() != null) {
            product.setDiscountValue(request.getDiscountValue());
        }
        if (request.getBrand() != null) {
            product.setBrand(request.getBrand());
        }
        if (request.getTags() != null) {
            product.setTags(request.getTags());
        }

        boolean imagesChanged = false;
        if (request.getRemoveImageUrls() != null && !request.getRemoveImageUrls().isEmpty()) {
            for (String imageUrl : request.getRemoveImageUrls()) {
                s3Service.deleteFile(imageUrl);
                productImageRepository.deleteByProductIdAndImageUrl(id, imageUrl);
            }
            imagesChanged = true;
        }

        if (request.getImages() != null && !request.getImages().isEmpty()) {
            int baseSortOrder = product.getImages().size();

            List<MultipartFile> imageList = request.getImages().stream()
                    .filter(img -> img != null && !img.isEmpty())
                    .limit(10 - baseSortOrder)
                    .toList();

            int sortOrder = baseSortOrder;
            for (MultipartFile image : imageList) {
                String imageUrl = s3Service.uploadFile(image, "products/" + id);
                if (imageUrl != null) {
                    jdbcTemplate.update(
                            "INSERT INTO product_images (id, product_id, image_url, image_name, image_type, sort_order, created_at) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                            UUID.randomUUID(), id, imageUrl, image.getOriginalFilename(), image.getContentType(), sortOrder++
                    );
                    log.info("Image inserted for product_id={}", id);
                    imagesChanged = true;
                }
            }
        }

        productRepository.save(product);
        if (imagesChanged) {
            entityManager.refresh(product);
        }
        return toResponseWithRelations(product);
    }

    @Transactional
    public ProductResponse addImagesToProduct(UUID id, List<MultipartFile> images) {
        Product product = productRepository.findByIdWithRelations(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        int sortOrder = product.getImages().size();

        if (sortOrder >= 10) {
            throw new IllegalStateException("Product already has maximum of 10 images");
        }

        List<MultipartFile> imageList = images.stream()
                .filter(img -> img != null && !img.isEmpty())
                .limit(10 - sortOrder)
                .toList();

        int currentSortOrder = sortOrder;
        for (MultipartFile image : imageList) {
            String imageUrl = s3Service.uploadFile(image, "products/" + id);
            if (imageUrl != null) {
                jdbcTemplate.update(
                        "INSERT INTO product_images (id, product_id, image_url, image_name, image_type, sort_order, created_at) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                        UUID.randomUUID(), id, imageUrl, image.getOriginalFilename(), image.getContentType(), currentSortOrder++
                );
            }
        }

        entityManager.refresh(product);
        return toResponseWithRelations(product);
    }

    @Transactional
    public ProductResponse activateProduct(UUID id) {
        Product product = productRepository.findByIdWithRelations(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        if (Boolean.TRUE.equals(product.getIsActive())) {
            throw new IllegalArgumentException("Product is already active");
        }

        product.setIsActive(true);
        Product savedProduct = productRepository.save(product);
        return toResponseWithRelations(savedProduct);
    }

    @Transactional
    public void deleteProduct(UUID id) {
        Product product = productRepository.findByIdWithRelations(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        product.setIsActive(false);
        productRepository.save(product);
    }

    @Transactional
    public void deleteProductImage(UUID productId, UUID imageId) {
        ProductImage productImage = productImageRepository.findByIdAndProductId(imageId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductImage", "id", imageId));
        String imageUrl = productImage.getImageUrl();
        s3Service.deleteFile(imageUrl);
        productImageRepository.delete(productImage);
    }

    private ProductResponse toResponseWithRelations(Product product) {
        ProductResponse response = productMapper.toResponse(product);
        List<String> imageUrls = product.getImages() != null
                ? product.getImages().stream().map(ProductImage::getImageUrl).toList()
                : List.of();
        response.setImageUrls(imageUrls);
        List<ProductImageResponse> images = product.getImages() != null
                ? product.getImages().stream()
                        .map(this::toProductImageResponse)
                        .toList()
                : List.of();
        response.setImages(images);
        if (product.getInventory() != null) {
            response.setInventory(inventoryMapper.toResponse(product.getInventory()));
        }
        return response;
    }

    private ProductImageResponse toProductImageResponse(ProductImage productImage) {
        ProductImageResponse response = new ProductImageResponse();
        response.setId(productImage.getId());
        response.setImageUrl(productImage.getImageUrl());
        response.setImageName(productImage.getImageName());
        response.setImageType(productImage.getImageType());
        response.setSortOrder(productImage.getSortOrder());
        return response;
    }

    private void validateDiscount(ProductRequest request) {
        if (request.getDiscountType() != null || request.getDiscountValue() != null) {
            if (request.getDiscountType() == null) {
                throw new IllegalArgumentException("discountType is required when discountValue is set");
            }
            if (request.getDiscountValue() == null) {
                throw new IllegalArgumentException("discountValue is required when discountType is set");
            }
            if (!List.of("PERCENTAGE", "FIXED").contains(request.getDiscountType())) {
                throw new IllegalArgumentException("discountType must be PERCENTAGE or FIXED");
            }
            if (request.getDiscountValue().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("discountValue must not be negative");
            }
        }
    }
}
