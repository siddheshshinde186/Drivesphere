package com.drivesphere.service;

import com.drivesphere.entity.*;
import com.drivesphere.repository.DriverTrustScoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriverTrustScoreService {

    private final DriverTrustScoreRepository trustScoreRepository;

    @Transactional
    public DriverTrustScore calculateAndSaveTrustScore(Driver driver) {
        // 1. Verification Factor (Max 25 pts)
        BigDecimal verificationFactor = BigDecimal.ZERO;
        if (driver.getVerification() != null) {
            VerificationStatus vStatus = driver.getVerification().getStatus();
            if (vStatus == VerificationStatus.APPROVED) {
                verificationFactor = BigDecimal.valueOf(25.00);
            } else if (vStatus == VerificationStatus.UNDER_REVIEW) {
                verificationFactor = BigDecimal.valueOf(12.00);
            } else {
                verificationFactor = BigDecimal.valueOf(5.00);
            }
        }

        // 2. Experience Factor (Max 20 pts: ~3.5 pts per year, capped at 20)
        int expYears = driver.getExperienceYears() != null ? driver.getExperienceYears() : 0;
        BigDecimal experienceFactor = BigDecimal.valueOf(Math.min(20.0, expYears * 3.5))
                .setScale(2, RoundingMode.HALF_UP);

        // 3. Customer Rating Factor (Max 25 pts: proportional to 5-star rating)
        BigDecimal rating = driver.getRating() != null ? driver.getRating() : BigDecimal.valueOf(5.0);
        BigDecimal ratingFactor = rating.divide(BigDecimal.valueOf(5.0), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(25.0))
                .setScale(2, RoundingMode.HALF_UP);

        // 4. Trip Completion Factor (Max 20 pts)
        int totalTrips = driver.getTotalTrips() != null ? driver.getTotalTrips() : 0;
        BigDecimal completionFactor = BigDecimal.valueOf(15.00);
        if (totalTrips >= 50) {
            completionFactor = BigDecimal.valueOf(20.00);
        } else if (totalTrips >= 20) {
            completionFactor = BigDecimal.valueOf(18.00);
        } else if (totalTrips >= 5) {
            completionFactor = BigDecimal.valueOf(16.00);
        }

        // 5. Incident / Cancellation Penalty (Max 10 pts penalty)
        BigDecimal cancellationRate = driver.getCancellationRate() != null
                ? driver.getCancellationRate() : BigDecimal.ZERO;
        BigDecimal incidentPenalty = cancellationRate.multiply(BigDecimal.valueOf(0.5))
                .min(BigDecimal.valueOf(10.00))
                .setScale(2, RoundingMode.HALF_UP);

        // Total Overall Score
        BigDecimal overallScore = verificationFactor
                .add(experienceFactor)
                .add(ratingFactor)
                .add(completionFactor)
                .subtract(incidentPenalty)
                .max(BigDecimal.ZERO)
                .min(BigDecimal.valueOf(100.00))
                .setScale(2, RoundingMode.HALF_UP);

        // Trust Level Tiering
        TrustLevel level;
        if (overallScore.compareTo(BigDecimal.valueOf(85.0)) >= 0) {
            level = TrustLevel.EXCELLENT;
        } else if (overallScore.compareTo(BigDecimal.valueOf(70.0)) >= 0) {
            level = TrustLevel.GOOD;
        } else if (overallScore.compareTo(BigDecimal.valueOf(50.0)) >= 0) {
            level = TrustLevel.AVERAGE;
        } else {
            level = TrustLevel.LOW;
        }

        String explanation = String.format(
                "Driver Trust Score: %.1f/100 (%s). Breakdown: Document Verification (%.1f/25), Professional Experience (%.1f/20), Customer Feedback Rating (%.1f/25), Trip Completion (%.1f/20), Cancellation Deduction (-%.1f). Note: Algorithmic academic reliability metric, not an official government license certification.",
                overallScore, level, verificationFactor, experienceFactor, ratingFactor, completionFactor, incidentPenalty
        );

        DriverTrustScore trustScore = trustScoreRepository.findByDriver(driver)
                .orElse(DriverTrustScore.builder().driver(driver).build());

        trustScore.setOverallScore(overallScore);
        trustScore.setTrustLevel(level);
        trustScore.setVerificationFactor(verificationFactor);
        trustScore.setExperienceFactor(experienceFactor);
        trustScore.setRatingFactor(ratingFactor);
        trustScore.setCompletionFactor(completionFactor);
        trustScore.setIncidentPenalty(incidentPenalty);
        trustScore.setExplanation(explanation);
        trustScore.setLastCalculatedAt(Instant.now());

        return trustScoreRepository.save(trustScore);
    }
}
