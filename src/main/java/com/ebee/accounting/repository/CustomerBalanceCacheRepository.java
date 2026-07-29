package com.ebee.accounting.repository;

import com.ebee.accounting.entity.CustomerBalanceCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerBalanceCacheRepository extends JpaRepository<CustomerBalanceCache, UUID> {

    Optional<CustomerBalanceCache> findByCustomerId(UUID customerId);
}
