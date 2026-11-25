package com.ebee.finance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** One movement of money into or out of a payment account. Amount is always positive. */
@Entity
@Table(name = "account_transactions")
public class AccountTransaction {

    public enum Direction { IN, OUT }

    /** Why money moved. Transfers and cheque deposits are internal and don't count as cash flow. */
    public enum Type {
        OPENING, CUSTOMER_RECEIPT, CUSTOMER_REFUND, SUPPLIER_PAYMENT, EXPENSE, COMMISSION_PAYOUT,
        TRANSFER, CHEQUE_DEPOSIT, OWNER_DEPOSIT, OWNER_WITHDRAWAL, ADJUSTMENT;

        public boolean isInternal() {
            return this == TRANSFER || this == CHEQUE_DEPOSIT;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private PaymentAccount account;

    @Column(name = "txn_date", nullable = false)
    private LocalDate txnDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Direction direction;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "txn_type", nullable = false, length = 30)
    private Type type;

    @Column(name = "reference_type", length = 30)
    private String referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "transfer_group")
    private UUID transferGroup;

    @Column(length = 500)
    private String description;

    @Column(name = "recorded_by")
    private UUID recordedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected AccountTransaction() {
    }

    public AccountTransaction(PaymentAccount account, LocalDate txnDate, Direction direction, BigDecimal amount,
                              Type type, String referenceType, UUID referenceId, UUID transferGroup,
                              String description, UUID recordedBy) {
        this.account = account;
        this.txnDate = txnDate;
        this.direction = direction;
        this.amount = amount;
        this.type = type;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.transferGroup = transferGroup;
        this.description = description;
        this.recordedBy = recordedBy;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /** +amount for IN, −amount for OUT. */
    public BigDecimal signedAmount() {
        return direction == Direction.IN ? amount : amount.negate();
    }

    public UUID getId() { return id; }
    public PaymentAccount getAccount() { return account; }
    public LocalDate getTxnDate() { return txnDate; }
    public Direction getDirection() { return direction; }
    public BigDecimal getAmount() { return amount; }
    public Type getType() { return type; }
    public String getReferenceType() { return referenceType; }
    public UUID getReferenceId() { return referenceId; }
    public UUID getTransferGroup() { return transferGroup; }
    public String getDescription() { return description; }
    public UUID getRecordedBy() { return recordedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
