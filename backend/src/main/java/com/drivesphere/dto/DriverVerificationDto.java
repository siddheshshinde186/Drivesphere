package com.drivesphere.dto;

import com.drivesphere.entity.VerificationStatus;
import lombok.*;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverVerificationDto {

    private Long id;
    private Long driverId;
    private String driverName;
    private String identityDocUrl;
    private String licenseDocUrl;
    private String policeVerificationDocUrl;
    private String medicalFitnessDocUrl;
    private String eyeTestDocUrl;
    private Integer drivingSkillScore;
    private VerificationStatus status;
    private String verifiedByName;
    private String adminNotes;
    private Instant submittedAt;
    private Instant verifiedAt;
}
