package com.ebee.product.repository;

import com.ebee.product.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {

    List<ProductImage> findByProductIdOrderBySortOrder(UUID productId);

    List<ProductImage> findByProductIdInOrderBySortOrder(List<UUID> productIds);

    void deleteByProductId(UUID productId);

    void deleteByProductIdAndImageUrl(UUID productId, String imageUrl);

    @Modifying(clearAutomatically = true)
    @Query(value = "INSERT INTO product_images (id, product_id, image_url, image_name, image_type, sort_order, created_at) VALUES (?1, ?2, ?3, ?4, ?5, ?6, CURRENT_TIMESTAMP)", nativeQuery = true)
    void insertImage(UUID id, UUID productId, String imageUrl, String imageName, String imageType, Integer sortOrder);
}
