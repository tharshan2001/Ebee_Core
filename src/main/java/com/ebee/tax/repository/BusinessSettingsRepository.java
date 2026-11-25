package com.ebee.tax.repository;

import com.ebee.tax.entity.BusinessSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessSettingsRepository extends JpaRepository<BusinessSettings, Short> {
}
