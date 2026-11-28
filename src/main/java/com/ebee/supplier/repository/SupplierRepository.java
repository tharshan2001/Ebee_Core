package com.ebee.supplier.repository;

import com.ebee.supplier.entity.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    Page<Supplier> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<Supplier> findByIsActiveTrue(Pageable pageable);

    @Query("SELECT s FROM Supplier s WHERE LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.contactPerson) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.phone) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.email) LIKE LOWER(CONCAT('%', :search, '%'))")
    List<Supplier> findBySearchTerm(@Param("search") String search);

    java.util.Optional<Supplier> findFirstByNameIgnoreCase(String name);

    @Query(value = "SELECT s FROM Supplier s WHERE s.outstandingBalance > 0 "
            + "AND (LOWER(s.name) LIKE :q OR LOWER(COALESCE(s.phone, '')) LIKE :q)",
           countQuery = "SELECT COUNT(s) FROM Supplier s WHERE s.outstandingBalance > 0 "
            + "AND (LOWER(s.name) LIKE :q OR LOWER(COALESCE(s.phone, '')) LIKE :q)")
    Page<Supplier> findDues(@Param("q") String q, Pageable pageable);

    /** One row: [count, total outstanding]. */
    @Query("SELECT COUNT(s), COALESCE(SUM(s.outstandingBalance), 0) FROM Supplier s WHERE s.outstandingBalance > 0")
    List<Object[]> duesTotals();

    java.util.Optional<Supplier> findFirstByOutstandingBalanceGreaterThanOrderByOutstandingBalanceDesc(java.math.BigDecimal amount);
}
