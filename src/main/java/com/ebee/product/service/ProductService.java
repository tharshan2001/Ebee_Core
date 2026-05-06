package com.ebee.product.service;

import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.product.dto.ProductRequest;
import com.ebee.product.dto.ProductResponse;
import com.ebee.product.entity.Product;
import com.ebee.product.mapper.ProductMapper;
import com.ebee.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        if (productRepository.existsBySku(request.getSku())) {
            throw new IllegalArgumentException("SKU already exists: " + request.getSku());
        }

        Product product = productMapper.toEntity(request);
        Product savedProduct = productRepository.save(product);
        return productMapper.toResponse(savedProduct);
    }

    public ProductResponse getProductById(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        return productMapper.toResponse(product);
    }

    public ProductResponse getProductBySku(String sku) {
        Product product = productRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "sku", sku));
        return productMapper.toResponse(product);
    }

    public List<ProductResponse> getAllProducts() {
        List<Product> products = productRepository.findAll();
        return productMapper.toResponseList(products);
    }

    public List<ProductResponse> getActiveProducts() {
        List<Product> products = productRepository.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .toList();
        return productMapper.toResponseList(products);
    }

    @Transactional
    public ProductResponse updateProduct(UUID id, ProductRequest request) {
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

        Product updatedProduct = productRepository.save(product);
        return productMapper.toResponse(updatedProduct);
    }

    @Transactional
    public void deleteProduct(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        product.setIsActive(false);
        productRepository.save(product);
    }

}
