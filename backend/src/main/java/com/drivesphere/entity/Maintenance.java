package com.drivesphere.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "maintenance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Maintenance extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", length = 30, nullable = false)
    private ServiceType serviceType;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Column(name = "next_service_date")
    private LocalDate nextServiceDate;

    @Column(name = "description", columnDefinition = "TEXT", nullable = false)
    private String description;

    @Builder.Default
    @Column(name = "cost", precision = 10, scale = 2, nullable = false)
    private BigDecimal cost = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", length = 30, nullable = false)
    private MaintenanceStatus status = MaintenanceStatus.SCHEDULED;

    @Column(name = "odometer_km", precision = 10, scale = 2)
    private BigDecimal odometerKm;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}
