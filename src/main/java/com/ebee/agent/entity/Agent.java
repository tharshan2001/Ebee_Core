package com.ebee.agent.entity;

import com.ebee.agent.enums.AgentCommissionType;
import com.ebee.common.enums.District;
import com.ebee.order.entity.Order;
import com.ebee.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "agents")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Agent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private String agentCode;

    @Column(nullable = false)
    private String phoneNumber;

    @Column(name = "commission_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal commissionRate;

    @Column(name = "total_sales", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalSales = BigDecimal.ZERO;

    @Column(name = "total_commission", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalCommission = BigDecimal.ZERO;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "commission_type", nullable = false)
    @Builder.Default
    private AgentCommissionType commissionType = AgentCommissionType.DEFAULT;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String address;

    @Column(nullable = false, length = 50)
    private District district;

    @Column(name = "nic_number")
    private String nicNumber;

    @Column(name = "nic_document_url")
    private String nicDocumentUrl;

    @Column(name = "nic_document_name")
    private String nicDocumentName;

    @Column(name = "nic_document_type")
    private String nicDocumentType;

    @Column(name = "bank_passbook_document_url")
    private String bankPassbookDocumentUrl;

    @Column(name = "bank_passbook_document_name")
    private String bankPassbookDocumentName;

    @Column(name = "bank_passbook_document_type")
    private String bankPassbookDocumentType;

    @Column(name = "bank_account_number")
    private String bankAccountNumber;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "bank_branch")
    private String bankBranch;

    @OneToMany(mappedBy = "agent")
    private List<Order> orders = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
