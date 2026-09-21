package com.drivesphere.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trips")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trip extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @Column(name = "start_latitude", precision = 10, scale = 7, nullable = false)
    private BigDecimal startLatitude;

    @Column(name = "start_longitude", precision = 10, scale = 7, nullable = false)
    private BigDecimal startLongitude;

    @Column(name = "dest_latitude", precision = 10, scale = 7, nullable = false)
    private BigDecimal destLatitude;

    @Column(name = "dest_longitude", precision = 10, scale = 7, nullable = false)
    private BigDecimal destLongitude;

    @Column(name = "current_latitude", precision = 10, scale = 7)
    private BigDecimal currentLatitude;

    @Column(name = "current_longitude", precision = 10, scale = 7)
    private BigDecimal currentLongitude;

    @Builder.Default
    @Column(name = "distance_km", precision = 8, scale = 2)
    private BigDecimal distanceKm = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "distance_traveled_km", precision = 8, scale = 2)
    private BigDecimal distanceTraveledKm = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", length = 30, nullable = false)
    private TripStatus status = TripStatus.NOT_STARTED;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Builder.Default
    @Column(name = "simulation_step")
    private Integer simulationStep = 0;
}
