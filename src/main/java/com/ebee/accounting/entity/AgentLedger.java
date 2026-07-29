package com.ebee.accounting.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "agent_ledger")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "agent_id", nullable = false)
    private UUID agentId;

    @Column(name = "commission_id")
    private UUID commissionId;

    @Column(name = "payout_id")
    private UUID payoutId;

    @Column(name = "transaction_type", nullable = false, length = 50)
    private String transactionType;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal debit = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal credit = BigDecimal.ZERO;

    @Column
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
