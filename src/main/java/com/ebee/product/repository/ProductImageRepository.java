package com.ebee.product.repository;

import com.ebee.product.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {

    List<ProductImage> findByProductIdOrderBySortOrder(UUID productId);

    void deleteByProductId(UUID productId);

    void deleteByProductIdAndImageUrl(UUID productId, String imageUrl);
}
