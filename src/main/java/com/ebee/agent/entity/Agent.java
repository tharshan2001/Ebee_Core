package com.ebee.agent.entity;

import com.ebee.agent.enums.AgentCommissionType;
import com.ebee.common.enums.District;
import com.ebee.order.entity.Order;
import com.ebee.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private String agentCode;

    @Column(nullable = false)
    private String phoneNumber;

    @Column(name = "commission_rate", nullable = false)
    private Double commissionRate;

    @Column(name = "total_sales", nullable = false)
    @Builder.Default
    private Double totalSales = 0.0;

    @Column(name = "total_commission", nullable = false)
    @Builder.Default
    private Double totalCommission = 0.0;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "commission_type", nullable = false)
    @Builder.Default
    private AgentCommissionType commissionType = AgentCommissionType.DEFAULT;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(length = 50)
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
