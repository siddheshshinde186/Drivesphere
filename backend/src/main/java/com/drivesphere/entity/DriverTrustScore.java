package com.drivesphere.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "driver_trust_scores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverTrustScore extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false, unique = true)
    private Driver driver;

    @Builder.Default
    @Column(name = "overall_score", precision = 5, scale = 2, nullable = false)
    private BigDecimal overallScore = BigDecimal.valueOf(80.00);

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "trust_level", length = 30, nullable = false)
    private TrustLevel trustLevel = TrustLevel.GOOD;

    @Builder.Default
    @Column(name = "verification_factor", precision = 5, scale = 2)
    private BigDecimal verificationFactor = BigDecimal.valueOf(25.00);

    @Builder.Default
    @Column(name = "experience_factor", precision = 5, scale = 2)
    private BigDecimal experienceFactor = BigDecimal.valueOf(15.00);

    @Builder.Default
    @Column(name = "rating_factor", precision = 5, scale = 2)
    private BigDecimal ratingFactor = BigDecimal.valueOf(25.00);

    @Builder.Default
    @Column(name = "completion_factor", precision = 5, scale = 2)
    private BigDecimal completionFactor = BigDecimal.valueOf(18.00);

    @Builder.Default
    @Column(name = "incident_penalty", precision = 5, scale = 2)
    private BigDecimal incidentPenalty = BigDecimal.ZERO;

    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "last_calculated_at")
    private Instant lastCalculatedAt;
}
