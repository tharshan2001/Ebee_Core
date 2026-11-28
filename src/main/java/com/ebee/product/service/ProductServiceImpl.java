package com.ebee.product.service;

import com.ebee.category.service.CategoryService;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.service.PrintTemplateService;
import com.ebee.product.dto.LabelData;
import com.ebee.common.port.StoragePort;
import com.ebee.inventory.mapper.InventoryMapper;
import com.ebee.inventory.service.InventoryService;
import com.ebee.product.dto.ProductImageResponse;
import com.ebee.product.dto.ProductRequest;
import com.ebee.product.dto.ProductResponse;
import com.ebee.product.dto.ProductSearchResponse;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final ProductImageRepository productImageRepository;
    private final StoragePort storagePort;
    private final JdbcTemplate jdbcTemplate;
    private final InventoryService inventoryService;
    private final InventoryMapper inventoryMapper;
    private final EntityManager entityManager;
    private final CategoryService categoryService;
    private final PrintTemplateService printTemplateService;
    private final com.ebee.tax.service.TaxService taxService;

    @Transactional
    @Override
    public ProductResponse createProduct(ProductRequest request) {
        if (request.getItemCode() == null || request.getItemCode().isBlank()) {
            request.setItemCode(generateItemCode());
        } else {
            request.setItemCode(request.getItemCode().trim());
        }
        if (productRepository.existsByItemCodeIgnoreCase(request.getItemCode())) {
            throw new IllegalArgumentException("Item code already exists: " + request.getItemCode());
        }

        categoryService.findByNameOrThrow(request.getCategory());
        validateDiscount(request);

        Product product = productMapper.toEntity(request);
        if (request.getTaxRateId() != null) {
            taxService.requireRate(request.getTaxRateId());
        }
        if (product.getPrice() == null) {
            product.setPrice(BigDecimal.ZERO);
        }
        if (product.getCostPrice() == null) {
            product.setCostPrice(BigDecimal.ZERO);
        }
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
                String imageUrl = storagePort.uploadFile(image, "products/" + productId, "Image_" + sortOrder);
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

    /** The next free ITM-nnnnn code; numbers someone already typed by hand are skipped. */
    private String generateItemCode() {
        String code;
        do {
            code = String.format("ITM-%05d", productRepository.nextItemCodeNumber());
        } while (productRepository.existsByItemCodeIgnoreCase(code));
        return code;
    }

    @Override
    public ProductResponse getProductById(UUID id) {
        Product product = productRepository.findByIdWithRelations(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        return toResponseWithRelations(product);
    }

    @Override
    public ProductResponse getProductByItemCode(String itemCode) {
        Product product = productRepository.findByItemCodeWithRelations(itemCode)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "itemCode", itemCode));
        return toResponseWithRelations(product);
    }

    @Override
    public List<ProductResponse> getAllProducts() {
        List<Product> products = productRepository.findAllWithInventoryAndImages();
        return buildResponsesWithBatchImages(products);
    }

    @Override
    public List<ProductResponse> getProductsByCategory(String category) {
        List<Product> products = productRepository.findByCategoryIgnoreCase(category);
        return buildResponsesWithBatchImages(products);
    }

    @Override
    public List<ProductResponse> getActiveProducts() {
        List<Product> products = productRepository.findAllWithInventoryAndImages().stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .toList();
        return buildResponsesWithBatchImages(products);
    }

    @Override
    public Page<ProductResponse> getAllProductsPaginated(Pageable pageable, String category, String stock) {
        Page<UUID> idPage;
        if (category != null && !category.isBlank()) {
            idPage = productRepository.findProductIdsByCategory(category, pageable);
        } else {
            idPage = productRepository.findProductIds(pageable);
        }
        return toProductResponsePage(idPage, pageable);
    }

    @Override
    public Page<ProductResponse> getActiveProductsPaginated(Pageable pageable, String category, String stock) {
        Page<UUID> idPage;
        if (category != null && !category.isBlank()) {
            idPage = productRepository.findActiveProductIdsByCategory(category, pageable);
        } else {
            idPage = productRepository.findActiveProductIds(pageable);
        }
        return toProductResponsePage(idPage, pageable);
    }

    @Override
    public Map<String, Object> getProductStats() {
        Map<String, Object> stats = new java.util.LinkedHashMap<>();
        BigDecimal minPrice = productRepository.findMinActivePrice();
        BigDecimal maxPrice = productRepository.findMaxActivePrice();
        stats.put("minPrice", minPrice != null ? minPrice : BigDecimal.ZERO);
        stats.put("maxPrice", maxPrice != null ? maxPrice : BigDecimal.ZERO);
        return stats;
    }

    @Override
    public Page<ProductResponse> searchProducts(String search, Pageable pageable, String category, String stock) {
        Page<UUID> idPage;
        if (category != null && !category.isBlank()) {
            idPage = productRepository.findProductIdsBySearchTermAndCategory(search, category, pageable);
        } else {
            idPage = productRepository.findProductIdsBySearchTerm(search, pageable);
        }
        return toProductResponsePage(idPage, pageable);
    }

    @Override
    public Page<ProductResponse> getFeaturedProducts(Pageable pageable) {
        return toProductResponsePage(productRepository.findFeaturedProductIds(pageable), pageable);
    }

    @Override
    public Page<ProductResponse> getDealProducts(Pageable pageable) {
        return toProductResponsePage(productRepository.findDealProductIds(pageable), pageable);
    }

    @Override
    public Page<ProductResponse> getBestSellerProducts(Pageable pageable) {
        return toProductResponsePage(productRepository.findBestSellerProductIds(pageable), pageable);
    }

    @Override
    public Page<ProductResponse> getNewArrivals(Pageable pageable) {
        return toProductResponsePage(productRepository.findNewArrivalProductIds(pageable), pageable);
    }

    private Page<ProductResponse> toProductResponsePage(Page<UUID> idPage, Pageable pageable) {
        if (!idPage.hasContent()) {
            return Page.empty();
        }
        List<Product> products = productRepository.findProductsWithRelationsByIds(Set.copyOf(idPage.getContent()));
        Map<UUID, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        List<ProductResponse> responses = idPage.getContent().stream()
                .map(id -> toResponseWithRelations(productMap.get(id)))
                .toList();
        return new PageImpl<>(responses, pageable, idPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    @Override
    public List<ProductResponse> searchProducts(String search) {
        return productRepository.findBySearchTerm(search).stream()
                .map(this::toResponseWithRelations)
                .toList();
    }

    @Override
    public ProductSearchResponse searchProductsAdvanced(String search, String category, BigDecimal minPrice, BigDecimal maxPrice, String sortBy, String sortOrder, int page, int size) {
        boolean hasSearch = search != null && !search.trim().isEmpty();
        boolean hasCategory = category != null && !category.trim().isEmpty();
        boolean hasPriceRange = minPrice != null && maxPrice != null;

        org.springframework.data.domain.Sort sort = org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.fromString(sortOrder != null ? sortOrder : "desc"),
                sortBy != null ? sortBy : "createdAt"
        );
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size, sort);

        Page<UUID> idPage;

        if (hasSearch && hasCategory && hasPriceRange) {
            idPage = productRepository.findProductIdsBySearchTermAndCategoryAndPriceRange(search.trim(), category.trim(), minPrice, maxPrice, pageable);
        } else if (hasSearch && hasCategory) {
            idPage = productRepository.findProductIdsBySearchTermAndCategory(search.trim(), category.trim(), pageable);
        } else if (hasSearch && hasPriceRange) {
            idPage = productRepository.findProductIdsBySearchTermAndPriceRange(search.trim(), minPrice, maxPrice, pageable);
        } else if (hasSearch) {
            idPage = productRepository.findProductIdsBySearchTerm(search.trim(), pageable);
        } else if (hasCategory && hasPriceRange) {
            idPage = productRepository.findActiveProductIdsByCategoryAndPriceRange(category.trim(), minPrice, maxPrice, pageable);
        } else if (hasCategory) {
            idPage = productRepository.findActiveProductIdsByCategory(category.trim(), pageable);
        } else if (hasPriceRange) {
            idPage = productRepository.findActiveProductIdsByPriceRange(minPrice, maxPrice, pageable);
        } else {
            idPage = productRepository.findActiveProductIds(pageable);
        }

        if (!idPage.hasContent()) {
            return new ProductSearchResponse(List.of(), false, idPage.getTotalPages() - 1, idPage.getTotalElements());
        }

        List<UUID> ids = idPage.getContent();
        boolean hasMore = idPage.hasNext();

        List<Product> products = productRepository.findProductsWithRelationsByIds(Set.copyOf(ids));
        Map<UUID, Product> productMap = products.stream().collect(Collectors.toMap(Product::getId, p -> p));
        List<ProductResponse> responses = ids.stream().map(id -> toResponseWithRelations(productMap.get(id))).toList();
        return new ProductSearchResponse(responses, hasMore, idPage.getTotalPages(), idPage.getTotalElements());
    }

    private List<ProductResponse> buildResponsesWithBatchImages(List<Product> products) {
        if (products.isEmpty()) return List.of();

        List<UUID> productIds = products.stream().map(Product::getId).toList();
        Map<UUID, List<ProductImage>> imagesByProductId = productImageRepository
                .findByProductIdsWithProductOrderBySortOrder(productIds)
                .stream()
                .collect(Collectors.groupingBy(pi -> pi.getProduct().getId()));

        return products.stream()
                .map(p -> {
                    ProductResponse response = productMapper.toResponse(p);
                    List<ProductImage> productImages = imagesByProductId.getOrDefault(p.getId(), List.of());
                    response.setImageUrls(productImages.stream().map(ProductImage::getImageUrl).toList());
                    response.setImages(productImages.stream().map(this::toProductImageResponse).toList());
                    response.setInventory(inventoryMapper.toProductSummary(p.getInventories()));
                    return response;
                })
                .toList();
    }

    @Transactional
    @Override
    public ProductResponse updateProduct(UUID id, ProductRequest request) {
        Product product = productRepository.findByIdWithRelations(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        if (request.getName() != null) {
            product.setName(request.getName());
        }
        if (request.getItemCode() != null && !request.getItemCode().isBlank()) {
            String itemCode = request.getItemCode().trim();
            if (!itemCode.equalsIgnoreCase(product.getItemCode()) &&
                productRepository.existsByItemCodeIgnoreCaseAndIdNot(itemCode, id)) {
                throw new IllegalArgumentException("Item code already exists: " + itemCode);
            }
            product.setItemCode(itemCode);
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
        // A price change on its own must still respect the maximum discount already on the product.
        if (request.getPrice() != null && !request.isMaxDiscountFieldsSet()
                && "FIXED".equals(product.getMaxDiscountType()) && product.getMaxDiscountValue() != null
                && product.getMaxDiscountValue().compareTo(request.getPrice()) > 0) {
            throw new IllegalArgumentException("Price " + request.getPrice() + " is below the product's maximum discount of "
                    + product.getMaxDiscountValue() + "; lower the maximum discount first");
        }
        if (request.isDiscountFieldsSet()) {
            product.setDiscountType(request.getDiscountType() != null ? request.getDiscountType().name() : null);
            product.setDiscountValue(request.getDiscountValue());
        }
        if (request.isMaxDiscountFieldsSet()) {
            product.setMaxDiscountType(request.getMaxDiscountType() != null ? request.getMaxDiscountType().name() : null);
            product.setMaxDiscountValue(request.getMaxDiscountValue());
        }
        if (request.getBrand() != null) {
            product.setBrand(request.getBrand());
        }
        if (request.getTaxRateId() != null) {
            product.setTaxRateId(taxService.requireRate(request.getTaxRateId()).getId());
        }
        if (request.getTracksExpiry() != null) {
            product.setTracksExpiry(request.getTracksExpiry());
        }
        if (request.getTags() != null) {
            product.setTags(request.getTags());
        }
        if (request.getSpecifications() != null) {
            product.setSpecifications(request.getSpecifications());
        }

        boolean imagesChanged = false;
        if (request.getRemoveImageUrls() != null && !request.getRemoveImageUrls().isEmpty()) {
            for (String imageUrl : request.getRemoveImageUrls()) {
                productImageRepository.deleteByProductIdAndImageUrl(id, imageUrl);
                storagePort.deleteFile(imageUrl);
            }
            product.getImages().removeIf(img -> request.getRemoveImageUrls().contains(img.getImageUrl()));
            jdbcTemplate.update("UPDATE product_images SET sort_order = s.new_order FROM (SELECT id, ROW_NUMBER() OVER (ORDER BY sort_order) - 1 AS new_order FROM product_images WHERE product_id = ?) s WHERE product_images.id = s.id", id);
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
                String imageUrl = storagePort.uploadFile(image, "products/" + id, "Image_" + sortOrder);
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
    @Override
    public ProductResponse addImagesToProduct(UUID id, List<MultipartFile> images) {
        productRepository.findByIdWithRelations(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        int sortOrder = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(sort_order), -1) + 1 FROM product_images WHERE product_id = ?",
                Integer.class, id);

        if (sortOrder >= 10) {
            throw new IllegalStateException("Product already has maximum of 10 images");
        }

        List<MultipartFile> imageList = images.stream()
                .filter(img -> img != null && !img.isEmpty())
                .limit(10 - sortOrder)
                .toList();

        int currentSortOrder = sortOrder;
        for (MultipartFile image : imageList) {
            String imageUrl = storagePort.uploadFile(image, "products/" + id, "Image_" + currentSortOrder);
            if (imageUrl != null) {
                jdbcTemplate.update(
                        "INSERT INTO product_images (id, product_id, image_url, image_name, image_type, sort_order, created_at) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                        UUID.randomUUID(), id, imageUrl, image.getOriginalFilename(), image.getContentType(), currentSortOrder++
                );
            }
        }

        Product refreshed = productRepository.findByIdWithRelations(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        return toResponseWithRelations(refreshed);
    }

    @Transactional
    @Override
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
    @Override
    public void deleteProduct(UUID id) {
        Product product = productRepository.findByIdWithRelations(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        product.setIsActive(false);
        productRepository.save(product);
    }

    @Transactional
    @Override
    public void deleteProductImage(UUID productId, UUID imageId) {
        ProductImage productImage = productImageRepository.findByIdAndProductId(imageId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductImage", "id", imageId));
        String imageUrl = productImage.getImageUrl();
        int deletedSortOrder = productImage.getSortOrder();
        productImageRepository.delete(productImage);
        productImageRepository.decrementSortOrderAfter(productId, deletedSortOrder);
        storagePort.deleteFile(imageUrl);
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
        response.setInventory(inventoryMapper.toProductSummary(product.getInventories()));
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

    @Transactional(readOnly = true)
    @Override
    public byte[] generateProductLabelPdf(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        var data = new LabelData(
                product.getName(),
                product.getItemCode(),
                product.getPrice(),
                product.getCategory(),
                product.getBrand()
        );

        return printTemplateService.renderToPdf("product-label", toMap(data));
    }

    private java.util.Map<String, Object> toMap(Object record) {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        try {
            for (var method : record.getClass().getRecordComponents()) {
                map.put(method.getName(), method.getAccessor().invoke(record));
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to convert record to map", e);
        }
        return map;
    }

    private void validateDiscount(ProductRequest request) {
        if (request.getDiscountType() != null || request.getDiscountValue() != null) {
            if (request.getDiscountType() == null) {
                throw new IllegalArgumentException("discountType is required when discountValue is set");
            }
            if (request.getDiscountValue() == null) {
                throw new IllegalArgumentException("discountValue is required when discountType is set");
            }
            if (request.getDiscountValue().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("discountValue must not be negative");
            }
            if (request.getDiscountType() == com.ebee.common.enums.DiscountType.PERCENTAGE
                    && request.getDiscountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException("PERCENTAGE discountValue must not exceed 100");
            }
        }
        if (request.getMaxDiscountType() != null || request.getMaxDiscountValue() != null) {
            if (request.getMaxDiscountType() == null) {
                throw new IllegalArgumentException("maxDiscountType is required when maxDiscountValue is set");
            }
            if (request.getMaxDiscountValue() == null) {
                throw new IllegalArgumentException("maxDiscountValue is required when maxDiscountType is set");
            }
            if (request.getMaxDiscountValue().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("maxDiscountValue must not be negative");
            }
            if (request.getMaxDiscountType() == com.ebee.common.enums.DiscountType.PERCENTAGE
                    && request.getMaxDiscountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException("PERCENTAGE maxDiscountValue must not exceed 100");
            }
            if (request.getPrice() != null
                    && request.getMaxDiscountType() == com.ebee.common.enums.DiscountType.FIXED
                    && request.getMaxDiscountValue().compareTo(request.getPrice()) > 0) {
                throw new IllegalArgumentException("FIXED maxDiscountValue must not exceed the product price");
            }
        }
    }
}
