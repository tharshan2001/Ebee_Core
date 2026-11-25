package com.ebee.location.entity;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "stock_transfers")
public class StockTransfer {

    public enum Status { DRAFT, IN_TRANSIT, RECEIVED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "transfer_number", nullable = false, unique = true, length = 30)
    private String transferNumber;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "from_location_id", nullable = false)
    private Location fromLocation;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "to_location_id", nullable = false)
    private Location toLocation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "dispatched_at")
    private LocalDateTime dispatchedAt;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "transfer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<StockTransferItem> items = new ArrayList<>();

    protected StockTransfer() {
    }

    public StockTransfer(String transferNumber, Location fromLocation, Location toLocation, String notes, UUID createdBy) {
        this.transferNumber = transferNumber;
        this.fromLocation = fromLocation;
        this.toLocation = toLocation;
        this.notes = notes;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addItem(StockTransferItem item) {
        item.setTransfer(this);
        items.add(item);
    }

    public UUID getId() { return id; }
    public String getTransferNumber() { return transferNumber; }
    public Location getFromLocation() { return fromLocation; }
    public Location getToLocation() { return toLocation; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getNotes() { return notes; }
    public UUID getCreatedBy() { return createdBy; }
    public LocalDateTime getDispatchedAt() { return dispatchedAt; }
    public void setDispatchedAt(LocalDateTime dispatchedAt) { this.dispatchedAt = dispatchedAt; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<StockTransferItem> getItems() { return items; }
}
