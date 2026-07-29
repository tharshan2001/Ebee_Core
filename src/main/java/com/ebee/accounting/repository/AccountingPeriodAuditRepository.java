package com.ebee.accounting.repository;

import com.ebee.accounting.entity.AccountingPeriodAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AccountingPeriodAuditRepository extends JpaRepository<AccountingPeriodAudit, UUID> {

    List<AccountingPeriodAudit> findByPeriodIdOrderByChangedAtDesc(UUID periodId);
}
