package com.drivesphere.service;

import com.drivesphere.dto.DriverVerificationDto;
import com.drivesphere.dto.DriverVerificationReviewRequest;
import com.drivesphere.entity.*;
import com.drivesphere.exception.ResourceNotFoundException;
import com.drivesphere.mapper.EntityDtoMapper;
import com.drivesphere.repository.DriverRepository;
import com.drivesphere.repository.DriverVerificationRepository;
import com.drivesphere.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DriverVerificationService {

    private final DriverVerificationRepository verificationRepository;
    private final DriverRepository driverRepository;
    private final UserRepository userRepository;
    private final DriverTrustScoreService trustScoreService;
    private final NotificationService notificationService;
    private final EntityDtoMapper mapper;

    @Transactional(readOnly = true)
    public List<DriverVerificationDto> getAllVerifications(VerificationStatus status) {
        if (status != null) {
            return verificationRepository.findByStatus(status).stream()
                    .map(mapper::toDriverVerificationDto)
                    .collect(Collectors.toList());
        }
        return verificationRepository.findAll().stream()
                .map(mapper::toDriverVerificationDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DriverVerificationDto getVerificationByDriverId(Long driverId) {
        return verificationRepository.findByDriverId(driverId)
                .map(mapper::toDriverVerificationDto)
                .orElseThrow(() -> new ResourceNotFoundException("Verification record not found for driver id: " + driverId));
    }

    @Transactional
    public DriverVerificationDto submitVerificationDocuments(Long driverId, DriverVerificationDto dto) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found with id: " + driverId));

        DriverVerification verification = verificationRepository.findByDriver(driver)
                .orElse(DriverVerification.builder().driver(driver).build());

        if (dto.getIdentityDocUrl() != null) verification.setIdentityDocUrl(dto.getIdentityDocUrl());
        if (dto.getLicenseDocUrl() != null) verification.setLicenseDocUrl(dto.getLicenseDocUrl());
        if (dto.getPoliceVerificationDocUrl() != null) verification.setPoliceVerificationDocUrl(dto.getPoliceVerificationDocUrl());
        if (dto.getMedicalFitnessDocUrl() != null) verification.setMedicalFitnessDocUrl(dto.getMedicalFitnessDocUrl());
        if (dto.getEyeTestDocUrl() != null) verification.setEyeTestDocUrl(dto.getEyeTestDocUrl());

        verification.setStatus(VerificationStatus.UNDER_REVIEW);
        verification.setSubmittedAt(Instant.now());

        verification = verificationRepository.save(verification);
        driver.setVerification(verification);

        trustScoreService.calculateAndSaveTrustScore(driver);

        return mapper.toDriverVerificationDto(verification);
    }

    @Transactional
    public DriverVerificationDto reviewVerification(Long verificationId, Long adminUserId, DriverVerificationReviewRequest request) {
        DriverVerification verification = verificationRepository.findById(verificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Verification not found with id: " + verificationId));

        User admin = userRepository.findById(adminUserId).orElse(null);
        verification.setVerifiedBy(admin);
        verification.setStatus(request.getStatus());
        verification.setAdminNotes(request.getAdminNotes());
        if (request.getDrivingSkillScore() != null) {
            verification.setDrivingSkillScore(request.getDrivingSkillScore());
        }
        verification.setVerifiedAt(Instant.now());

        Driver driver = verification.getDriver();
        if (driver != null) {
            if (request.getStatus() == VerificationStatus.APPROVED) {
                driver.setAvailable(true);
                if (driver.getUser() != null) {
                    driver.getUser().setVerified(true);
                    userRepository.save(driver.getUser());

                    notificationService.createNotification(
                            driver.getUser(),
                            "Driver Verification Approved",
                            "Congratulations! Your driving license and background verification have been approved. You are now eligible for customer trip assignments.",
                            NotificationType.VERIFICATION,
                            "DRIVER_VERIFICATION",
                            verification.getId()
                    );
                }
            } else if (request.getStatus() == VerificationStatus.REJECTED) {
                driver.setAvailable(false);
                if (driver.getUser() != null) {
                    notificationService.createNotification(
                            driver.getUser(),
                            "Driver Verification Update",
                            "Your verification application was rejected: " + (request.getAdminNotes() != null ? request.getAdminNotes() : "Please re-upload valid documents."),
                            NotificationType.VERIFICATION,
                            "DRIVER_VERIFICATION",
                            verification.getId()
                    );
                }
            }
            driverRepository.save(driver);
            trustScoreService.calculateAndSaveTrustScore(driver);
        }

        return mapper.toDriverVerificationDto(verificationRepository.save(verification));
    }
}
