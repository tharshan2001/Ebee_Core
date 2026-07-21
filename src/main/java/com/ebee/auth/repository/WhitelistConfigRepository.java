package com.ebee.auth.repository;

import com.ebee.auth.entity.WhitelistConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WhitelistConfigRepository extends JpaRepository<WhitelistConfig, Integer> {

    Optional<WhitelistConfig> findById(Integer id);
}
