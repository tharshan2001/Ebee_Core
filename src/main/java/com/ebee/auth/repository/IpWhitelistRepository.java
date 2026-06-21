package com.ebee.auth.repository;

import com.ebee.auth.entity.IpWhitelist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IpWhitelistRepository extends JpaRepository<IpWhitelist, UUID> {

    List<IpWhitelist> findByUserId(UUID userId);

    Optional<IpWhitelist> findByUserIdAndIpAddress(UUID userId, String ipAddress);

    boolean existsByUserIdAndIpAddress(UUID userId, String ipAddress);
}
