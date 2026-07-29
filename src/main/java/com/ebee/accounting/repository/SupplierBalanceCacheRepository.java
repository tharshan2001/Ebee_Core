package com.ebee.accounting.repository;

import com.ebee.accounting.entity.SupplierBalanceCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupplierBalanceCacheRepository extends JpaRepository<SupplierBalanceCache, UUID> {

    Optional<SupplierBalanceCache> findBySupplierId(UUID supplierId);
}
