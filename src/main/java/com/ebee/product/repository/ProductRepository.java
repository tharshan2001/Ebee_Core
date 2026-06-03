package com.ebee.product.repository;

import com.ebee.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByItemCode(String itemCode);

    boolean existsByItemCode(String itemCode);

    boolean existsByItemCodeAndIdNot(String itemCode, UUID id);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.inventory LEFT JOIN FETCH p.images WHERE p.id = :id")
    Optional<Product> findByIdWithRelations(@Param("id") UUID id);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.inventory")
    List<Product> findAllWithInventory();

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.inventory LEFT JOIN FETCH p.images WHERE p.itemCode = :itemCode")
    Optional<Product> findByItemCodeWithRelations(@Param("itemCode") String itemCode);

    List<Product> findByCategoryIgnoreCase(String category);
}
