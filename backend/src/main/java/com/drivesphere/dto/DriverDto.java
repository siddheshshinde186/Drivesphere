package com.drivesphere.dto;

import com.drivesphere.entity.VerificationStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverDto {

    private Long id;
    private Long userId;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String licenseNumber;
    private LocalDate licenseExpiryDate;
    private Integer experienceYears;
    private BigDecimal rating;
    private Integer totalTrips;
    private BigDecimal cancellationRate;
    private boolean available;
    private BigDecimal dailyRate;
    private String languages;
    private String address;
    private String profilePhotoUrl;
    private VerificationStatus verificationStatus;
    private DriverTrustScoreDto trustScore;
    private Long currentVehicleId;
    private String currentVehicleName;
}
