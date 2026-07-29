package com.ebee.accounting.repository;

import com.ebee.accounting.entity.FiscalYear;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FiscalYearRepository extends JpaRepository<FiscalYear, UUID> {

    Optional<FiscalYear> findByFiscalYear(Integer fiscalYear);

    @Query("SELECT f FROM FiscalYear f WHERE f.isActive = true ORDER BY f.fiscalYear DESC")
    Optional<FiscalYear> findLatestActive();

    @Query("SELECT f FROM FiscalYear f WHERE f.startDate <= CURRENT_DATE AND f.endDate >= CURRENT_DATE AND f.isActive = true")
    Optional<FiscalYear> findCurrent();
}
