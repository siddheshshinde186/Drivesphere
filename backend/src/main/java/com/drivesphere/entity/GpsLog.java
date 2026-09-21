package com.drivesphere.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "gps_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GpsLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "latitude", precision = 10, scale = 7, nullable = false)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7, nullable = false)
    private BigDecimal longitude;

    @Builder.Default
    @Column(name = "speed_kmh", precision = 6, scale = 2)
    private BigDecimal speedKmh = BigDecimal.ZERO;

    @Column(name = "heading_degrees", precision = 5, scale = 2)
    private BigDecimal headingDegrees;

    @Column(name = "battery_level_percent", precision = 5, scale = 2)
    private BigDecimal batteryLevelPercent;

    @Column(name = "fuel_level_percent", precision = 5, scale = 2)
    private BigDecimal fuelLevelPercent;

    @Builder.Default
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt = Instant.now();
}
