package com.drivesphere.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "drivers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Driver extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "license_number", length = 50, nullable = false, unique = true)
    private String licenseNumber;

    @Column(name = "license_expiry_date", nullable = false)
    private LocalDate licenseExpiryDate;

    @Builder.Default
    @Column(name = "experience_years", nullable = false)
    private Integer experienceYears = 0;

    @Builder.Default
    @Column(name = "rating", precision = 3, scale = 2)
    private BigDecimal rating = BigDecimal.valueOf(5.00);

    @Builder.Default
    @Column(name = "total_trips")
    private Integer totalTrips = 0;

    @Builder.Default
    @Column(name = "cancellation_rate", precision = 5, scale = 2)
    private BigDecimal cancellationRate = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "is_available", nullable = false)
    private boolean available = true;

    @Builder.Default
    @Column(name = "is_online", nullable = false)
    private boolean online = false;

    @Column(name = "last_seen_at")
    private java.time.LocalDateTime lastSeenAt;

    @Builder.Default
    @Column(name = "daily_rate", precision = 10, scale = 2, nullable = false)
    private BigDecimal dailyRate = BigDecimal.valueOf(800.00);

    @Column(name = "languages", length = 150)
    private String languages;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "profile_photo_url", length = 500)
    private String profilePhotoUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_vehicle_id")
    private Vehicle currentVehicle;

    @OneToOne(mappedBy = "driver", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private DriverVerification verification;

    @OneToOne(mappedBy = "driver", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private DriverTrustScore trustScore;
}
