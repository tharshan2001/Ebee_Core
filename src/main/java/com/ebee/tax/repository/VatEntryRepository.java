package com.ebee.tax.repository;

import com.ebee.tax.entity.VatEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface VatEntryRepository extends JpaRepository<VatEntry, UUID> {

    /** Rows: [direction, sourceType, Σ taxable, Σ tax] for the period (inclusive). */
    @Query("SELECT v.direction, v.sourceType, COALESCE(SUM(v.taxableAmount), 0), COALESCE(SUM(v.taxAmount), 0) "
            + "FROM VatEntry v WHERE v.entryDate >= :from AND v.entryDate <= :to "
            + "GROUP BY v.direction, v.sourceType")
    List<Object[]> summarize(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT COALESCE(SUM(v.taxAmount), 0) FROM VatEntry v WHERE v.direction = :direction AND v.entryDate <= :asOf")
    java.math.BigDecimal sumTaxAsOf(@Param("direction") com.ebee.tax.entity.VatEntry.Direction direction,
                                    @Param("asOf") LocalDate asOf);
}
