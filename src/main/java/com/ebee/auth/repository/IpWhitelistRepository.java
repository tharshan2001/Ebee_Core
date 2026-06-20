package com.ebee.auth.repository;

import com.ebee.auth.entity.IpWhitelist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IpWhitelistRepository extends JpaRepository<IpWhitelist, UUID> {

    List<IpWhitelist> findByUserId(UUID userId);

    Optional<IpWhitelist> findByUserIdAndIpAddress(UUID userId, String ipAddress);

    boolean existsByUserIdAndIpAddress(UUID userId, String ipAddress);
}
