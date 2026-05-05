package com.ebee.agent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "agents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Agent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID userId;

    @Column(nullable = false)
    private String agentCode;

    @Column(nullable = false)
    private String phoneNumber;

    @Column(name = "commission_rate", nullable = false)
    private Double commissionRate;

    @Column(name = "total_sales", columnDefinition = "DECIMAL(10,2) DEFAULT 0.00")
    @Builder.Default
    private Double totalSales = 0.0;

    @Column(name = "total_commission", columnDefinition = "DECIMAL(10,2) DEFAULT 0.00")
    @Builder.Default
    private Double totalCommission = 0.0;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
