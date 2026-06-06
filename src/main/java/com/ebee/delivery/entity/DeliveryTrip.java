package com.ebee.delivery.entity;

import com.ebee.delivery.enums.TripStatus;
import com.ebee.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "delivery_trips")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryTrip {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "trip_date", nullable = false)
    private LocalDate tripDate;

    @Column(nullable = false)
    private String district;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TripStatus status = TripStatus.PLANNED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_driver")
    private User assignedDriver;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
