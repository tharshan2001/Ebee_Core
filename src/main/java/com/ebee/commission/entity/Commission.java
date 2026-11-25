package com.ebee.commission.entity;

import com.ebee.agent.entity.Agent;
import com.ebee.commission.enums.CommissionStatus;
import com.ebee.order.entity.Order;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "commissions")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Commission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config_id")
    private CommissionConfig config;

    @Column(name = "order_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal orderAmount;

    @Column(name = "commission_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionRate;

    @Column(name = "commission_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal commissionAmount;

    /** Amount clawed back after payment because the order was (partly) returned or cancelled. */
    @Column(name = "clawback_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal clawbackAmount;

    /** Cash actually paid (commissionAmount minus clawback recovered at payment). Null on pre-V124 rows. */
    @Column(name = "paid_amount", precision = 12, scale = 2)
    private BigDecimal paidAmount;

    public BigDecimal getClawbackAmount() {
        return clawbackAmount == null ? BigDecimal.ZERO : clawbackAmount;
    }

    /** What the agent keeps from this commission after clawbacks. */
    public BigDecimal getEarnedAmount() {
        return commissionAmount.subtract(getClawbackAmount());
    }

    /** Cash paid out for this commission; falls back to commissionAmount for rows paid before V124. */
    public BigDecimal getNetPaidAmount() {
        return paidAmount != null ? paidAmount : commissionAmount;
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CommissionStatus status = CommissionStatus.PENDING;

    @Column(name = "calculated_at")
    private LocalDateTime calculatedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "error_message")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void applyDefaults() {
        if (clawbackAmount == null) {
            clawbackAmount = BigDecimal.ZERO;
        }
    }
}
