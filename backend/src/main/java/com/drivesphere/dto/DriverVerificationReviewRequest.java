package com.drivesphere.dto;

import com.drivesphere.entity.VerificationStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverVerificationReviewRequest {

    @NotNull(message = "Verification status is required")
    private VerificationStatus status;

    private String adminNotes;
    private Integer drivingSkillScore;
}
