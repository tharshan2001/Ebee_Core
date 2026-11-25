package com.ebee.product.repository;

import com.ebee.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByItemCode(String itemCode);

    List<Product> findByItemCodeIn(Collection<String> itemCodes);

    boolean existsByItemCode(String itemCode);

    boolean existsByItemCodeAndIdNot(String itemCode, UUID id);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.inventories LEFT JOIN FETCH p.images WHERE p.id = :id")
    Optional<Product> findByIdWithRelations(@Param("id") UUID id);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.inventories")
    List<Product> findAllWithInventory();

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.inventories LEFT JOIN FETCH p.images")
    List<Product> findAllWithInventoryAndImages();

    @Query("SELECT p.id FROM Product p ORDER BY p.createdAt DESC")
    Page<UUID> findProductIds(Pageable pageable);

    @Query("SELECT p.id FROM Product p WHERE p.category = :category ORDER BY p.createdAt DESC")
    Page<UUID> findProductIdsByCategory(@Param("category") String category, Pageable pageable);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.inventories LEFT JOIN FETCH p.images WHERE p.id IN :ids")
    List<Product> findProductsWithRelationsByIds(@Param("ids") Set<UUID> ids);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.inventories LEFT JOIN FETCH p.images WHERE p.itemCode = :itemCode")
    Optional<Product> findByItemCodeWithRelations(@Param("itemCode") String itemCode);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.inventories LEFT JOIN FETCH p.images WHERE LOWER(p.category) = LOWER(:category)")
    List<Product> findByCategoryIgnoreCase(@Param("category") String category);

    @Query("SELECT COUNT(DISTINCT p.category) FROM Product p WHERE p.category IS NOT NULL")
    Long countDistinctCategories();

    @Query(value = "SELECT p.category, COUNT(DISTINCT p.id), COALESCE(SUM(oi.quantity), 0), COALESCE(SUM(oi.subtotal), 0) FROM products p LEFT JOIN order_items oi ON oi.product_id = p.id LEFT JOIN orders o ON o.id = oi.order_id AND o.status NOT IN ('CANCELLED', 'RETURNED') AND o.created_at >= :start AND o.created_at < :end GROUP BY p.category ORDER BY COALESCE(SUM(oi.subtotal), 0) DESC", nativeQuery = true)
    List<Object[]> findProductCategorySummary(@Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

    @Query("SELECT p.id FROM Product p WHERE (LOWER(p.itemCode) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))) AND p.isActive = true ORDER BY p.createdAt DESC")
    Page<UUID> findProductIdsBySearchTerm(@Param("search") String search, Pageable pageable);

    @Query("SELECT p.id FROM Product p WHERE (LOWER(p.itemCode) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))) AND p.category = :category AND p.isActive = true ORDER BY p.createdAt DESC")
    Page<UUID> findProductIdsBySearchTermAndCategory(@Param("search") String search, @Param("category") String category, Pageable pageable);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.inventories LEFT JOIN FETCH p.images WHERE (p.name LIKE %:search% OR p.itemCode LIKE %:search% OR p.category LIKE %:search%) AND p.isActive = true")
    List<Product> findBySearchTerm(@Param("search") String search);

    @Query("SELECT p.id FROM Product p WHERE p.isActive = true ORDER BY p.createdAt DESC")
    Page<UUID> findActiveProductIds(Pageable pageable);

    @Query("SELECT p.id FROM Product p WHERE p.isActive = true AND p.category = :category ORDER BY p.createdAt DESC")
    Page<UUID> findActiveProductIdsByCategory(@Param("category") String category, Pageable pageable);

    long countByIsActiveTrue();

    @Query("SELECT COUNT(DISTINCT i.product.id) FROM Inventory i WHERE i.quantity <= COALESCE(i.reorderLevel, 0)")
    long countLowStockProducts();

    @Query("SELECT p.id FROM Product p WHERE p.isActive = true AND p.featured = true ORDER BY p.createdAt DESC")
    Page<UUID> findFeaturedProductIds(Pageable pageable);

    @Query("SELECT p.id FROM Product p WHERE p.isActive = true AND p.discountType IS NOT NULL AND p.discountValue > 0 ORDER BY p.createdAt DESC")
    Page<UUID> findDealProductIds(Pageable pageable);

    @Query("SELECT oi.product.id FROM OrderItem oi WHERE oi.order.status NOT IN ('CANCELLED', 'RETURNED') AND oi.product.isActive = true GROUP BY oi.product.id ORDER BY SUM(oi.quantity) DESC")
    Page<UUID> findBestSellerProductIds(Pageable pageable);

    @Query("SELECT p.id FROM Product p WHERE p.isActive = true ORDER BY p.createdAt DESC")
    Page<UUID> findNewArrivalProductIds(Pageable pageable);

    @Query("SELECT p.id FROM Product p WHERE p.isActive = true AND p.price >= :minPrice AND p.price <= :maxPrice ORDER BY p.createdAt DESC")
    Page<UUID> findActiveProductIdsByPriceRange(@Param("minPrice") BigDecimal minPrice, @Param("maxPrice") BigDecimal maxPrice, Pageable pageable);

    @Query("SELECT p.id FROM Product p WHERE p.isActive = true AND p.category = :category AND p.price >= :minPrice AND p.price <= :maxPrice ORDER BY p.createdAt DESC")
    Page<UUID> findActiveProductIdsByCategoryAndPriceRange(@Param("category") String category, @Param("minPrice") BigDecimal minPrice, @Param("maxPrice") BigDecimal maxPrice, Pageable pageable);

    @Query("SELECT p.id FROM Product p WHERE (LOWER(p.itemCode) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))) AND p.isActive = true AND p.price >= :minPrice AND p.price <= :maxPrice ORDER BY p.createdAt DESC")
    Page<UUID> findProductIdsBySearchTermAndPriceRange(@Param("search") String search, @Param("minPrice") BigDecimal minPrice, @Param("maxPrice") BigDecimal maxPrice, Pageable pageable);

    @Query("SELECT p.id FROM Product p WHERE (LOWER(p.itemCode) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))) AND p.category = :category AND p.isActive = true AND p.price >= :minPrice AND p.price <= :maxPrice ORDER BY p.createdAt DESC")
    Page<UUID> findProductIdsBySearchTermAndCategoryAndPriceRange(@Param("search") String search, @Param("category") String category, @Param("minPrice") BigDecimal minPrice, @Param("maxPrice") BigDecimal maxPrice, Pageable pageable);

    @Query("SELECT MIN(p.price) FROM Product p WHERE p.isActive = true")
    BigDecimal findMinActivePrice();

    @Query("SELECT MAX(p.price) FROM Product p WHERE p.isActive = true")
    BigDecimal findMaxActivePrice();
}
