package com.ebee.category.repository;

import com.ebee.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByIsActiveTrueOrderByName();

    Optional<Category> findByIdAndIsActiveTrue(UUID id);

    Optional<Category> findByNameIgnoreCase(String name);

    Optional<Category> findByNameIgnoreCaseAndIsActiveTrue(String name);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIsActiveTrue(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    @Query(value = "SELECT category, COUNT(*) FROM products WHERE is_active = true AND category IS NOT NULL GROUP BY category", nativeQuery = true)
    List<Object[]> countActiveProductsByCategory();
}
