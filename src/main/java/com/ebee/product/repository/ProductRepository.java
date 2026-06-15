package com.ebee.product.repository;

import com.ebee.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByItemCode(String itemCode);

    List<Product> findByItemCodeIn(Collection<String> itemCodes);

    boolean existsByItemCode(String itemCode);

    boolean existsByItemCodeAndIdNot(String itemCode, UUID id);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.inventory LEFT JOIN FETCH p.images WHERE p.id = :id")
    Optional<Product> findByIdWithRelations(@Param("id") UUID id);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.inventory")
    List<Product> findAllWithInventory();

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.inventory LEFT JOIN FETCH p.images")
    List<Product> findAllWithInventoryAndImages();

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.inventory LEFT JOIN FETCH p.images")
    Page<Product> findAllWithInventoryAndImages(Pageable pageable);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.inventory LEFT JOIN FETCH p.images WHERE p.itemCode = :itemCode")
    Optional<Product> findByItemCodeWithRelations(@Param("itemCode") String itemCode);

    List<Product> findByCategoryIgnoreCase(String category);

    @Query("SELECT COUNT(DISTINCT p.category) FROM Product p WHERE p.category IS NOT NULL")
    Long countDistinctCategories();

    @Query(value = "SELECT p.category, COUNT(DISTINCT p.id), COALESCE(SUM(oi.quantity), 0), COALESCE(SUM(oi.subtotal), 0) FROM products p LEFT JOIN order_items oi ON oi.product_id = p.id LEFT JOIN orders o ON o.id = oi.order_id AND o.status NOT IN ('CANCELLED', 'RETURNED') AND o.created_at >= :start AND o.created_at < :end GROUP BY p.category ORDER BY COALESCE(SUM(oi.subtotal), 0) DESC", nativeQuery = true)
    List<Object[]> findProductCategorySummary(@Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.inventory LEFT JOIN FETCH p.images WHERE (p.name LIKE %:search% OR p.itemCode LIKE %:search% OR p.category LIKE %:search%) AND p.isActive = true")
    Page<Product> findBySearchTerm(@Param("search") String search, Pageable pageable);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.inventory LEFT JOIN FETCH p.images WHERE (p.name LIKE %:search% OR p.itemCode LIKE %:search% OR p.category LIKE %:search%) AND p.isActive = true")
    List<Product> findBySearchTerm(@Param("search") String search);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.inventory LEFT JOIN FETCH p.images WHERE p.isActive = true")
    Page<Product> findAllActiveWithInventoryAndImages(Pageable pageable);
}
