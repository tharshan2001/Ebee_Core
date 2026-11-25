package com.ebee.pricing.repository;

import com.ebee.pricing.entity.ProductGroupPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductGroupPriceRepository extends JpaRepository<ProductGroupPrice, UUID> {
    Optional<ProductGroupPrice> findByProductIdAndPriceGroupId(UUID productId, UUID priceGroupId);
    List<ProductGroupPrice> findByPriceGroupIdAndProductIdIn(UUID priceGroupId, Collection<UUID> productIds);
    List<ProductGroupPrice> findByProductId(UUID productId);
}
