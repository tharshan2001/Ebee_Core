package com.ebee.accounting.repository;

import com.ebee.accounting.entity.AccountingPeriodTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AccountingPeriodTransactionRepository extends JpaRepository<AccountingPeriodTransaction, UUID> {

    List<AccountingPeriodTransaction> findByPeriodIdOrderByRecordedAtDesc(UUID periodId);

    List<AccountingPeriodTransaction> findByFiscalYearAndMonthOrderByRecordedAtDesc(Integer fiscalYear, Integer month);
}
