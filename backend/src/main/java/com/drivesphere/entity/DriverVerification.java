package com.drivesphere.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "driver_verifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverVerification extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false, unique = true)
    private Driver driver;

    @Column(name = "identity_doc_url", length = 500)
    private String identityDocUrl;

    @Column(name = "license_doc_url", length = 500)
    private String licenseDocUrl;

    @Column(name = "police_verification_doc_url", length = 500)
    private String policeVerificationDocUrl;

    @Column(name = "medical_fitness_doc_url", length = 500)
    private String medicalFitnessDocUrl;

    @Column(name = "eye_test_doc_url", length = 500)
    private String eyeTestDocUrl;

    @Builder.Default
    @Column(name = "driving_skill_score")
    private Integer drivingSkillScore = 85;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", length = 30, nullable = false)
    private VerificationStatus status = VerificationStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by_user_id")
    private User verifiedBy;

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;
}
