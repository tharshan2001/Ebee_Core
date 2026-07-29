package com.ebee.accounting.repository;

import com.ebee.accounting.entity.AccountingPeriod;
import com.ebee.accounting.enums.PeriodStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountingPeriodRepository extends JpaRepository<AccountingPeriod, UUID> {

    Optional<AccountingPeriod> findByMonthAndYear(Integer month, Integer year);

    List<AccountingPeriod> findByYear(Integer year);

    List<AccountingPeriod> findByStatus(PeriodStatus status);

    @Query("SELECT p FROM AccountingPeriod p WHERE p.year = :year ORDER BY p.month")
    List<AccountingPeriod> findByYearOrderByMonth(@Param("year") Integer year);

    @Query("SELECT p FROM AccountingPeriod p WHERE p.fiscalYear.id = :fiscalYearId ORDER BY p.month")
    List<AccountingPeriod> findByFiscalYearIdOrderByMonth(@Param("fiscalYearId") UUID fiscalYearId);

    @Query("SELECT p FROM AccountingPeriod p WHERE p.status = 'OPEN' ORDER BY p.year DESC, p.month DESC")
    List<AccountingPeriod> findOpenPeriods();

    @Query("SELECT p FROM AccountingPeriod p WHERE p.year = :year AND p.month = :month")
    Optional<AccountingPeriod> findByYearAndMonth(@Param("year") Integer year, @Param("month") Integer month);
}
