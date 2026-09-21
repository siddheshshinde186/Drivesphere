package com.drivesphere.dto;

import com.drivesphere.entity.TrustLevel;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverTrustScoreDto {

    private BigDecimal overallScore;
    private TrustLevel trustLevel;
    private BigDecimal verificationFactor;
    private BigDecimal experienceFactor;
    private BigDecimal ratingFactor;
    private BigDecimal completionFactor;
    private BigDecimal incidentPenalty;
    private String explanation;
    private Instant lastCalculatedAt;
}
