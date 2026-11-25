package com.ebee.tax.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One taxable event. Amounts are signed: a credit note or purchase return is a negative entry
 * dated when it happened, so it reduces VAT in that period.
 */
@Entity
@Table(name = "vat_entries")
public class VatEntry {

    public enum Direction { OUTPUT, INPUT }

    public enum SourceType { ORDER, CREDIT_NOTE, PURCHASE_RECEIPT, PURCHASE_RETURN }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Direction direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private SourceType sourceType;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(length = 100)
    private String reference;

    @Column(name = "taxable_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxableAmount;

    @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected VatEntry() {
    }

    public VatEntry(LocalDate entryDate, Direction direction, SourceType sourceType, UUID sourceId,
                    String reference, BigDecimal taxableAmount, BigDecimal taxAmount) {
        this.entryDate = entryDate;
        this.direction = direction;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.reference = reference;
        this.taxableAmount = taxableAmount;
        this.taxAmount = taxAmount;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public LocalDate getEntryDate() { return entryDate; }
    public Direction getDirection() { return direction; }
    public SourceType getSourceType() { return sourceType; }
    public UUID getSourceId() { return sourceId; }
    public String getReference() { return reference; }
    public BigDecimal getTaxableAmount() { return taxableAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
}
