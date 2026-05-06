package com.ebee.agent.entity;

import com.ebee.order.entity.Order;
import com.ebee.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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

    @Column(name = "total_sales", columnDefinition = "DECIMAL(10,2) DEFAULT 0.00")
    @Builder.Default
    private Double totalSales = 0.0;

    @Column(name = "total_commission", columnDefinition = "DECIMAL(10,2) DEFAULT 0.00")
    @Builder.Default
    private Double totalCommission = 0.0;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "nic_number")
    private String nicNumber;

    @Lob
    @Column(name = "nic_document", columnDefinition = "BYTEA")
    private byte[] nicDocument;

    @Column(name = "nic_document_name")
    private String nicDocumentName;

    @Column(name = "nic_document_type")
    private String nicDocumentType;

    @Lob
    @Column(name = "bank_passbook_document", columnDefinition = "BYTEA")
    private byte[] bankPassbookDocument;

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

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
