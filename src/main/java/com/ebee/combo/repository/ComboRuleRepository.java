package com.ebee.combo.repository;

import com.ebee.combo.entity.ComboRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ComboRuleRepository extends JpaRepository<ComboRule, UUID> {

    Optional<ComboRule> findByComboId(UUID comboId);
}
