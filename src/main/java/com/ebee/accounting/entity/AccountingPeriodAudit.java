package com.ebee.accounting.entity;

import com.ebee.accounting.enums.PeriodStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "accounting_period_audit")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountingPeriodAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "period_id", nullable = false)
    private UUID periodId;

    @Column(name = "fiscal_year", nullable = false)
    private Integer fiscalYear;

    @Column(nullable = false)
    private Integer month;

    @Column(nullable = false)
    private Integer year;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PeriodStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PeriodStatus newStatus;

    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Column
    private String reason;
}
