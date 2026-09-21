package com.drivesphere.mapper;

import com.drivesphere.dto.*;
import com.drivesphere.entity.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Clean, type-safe mapper converting JPA Entities to DTO representations.
 */
@Component
public class EntityDtoMapper {

    public UserDto toUserDto(User user) {
        if (user == null) return null;

        RoleType primaryRole = user.getRoles() != null && !user.getRoles().isEmpty()
                ? user.getRoles().iterator().next().getName()
                : RoleType.ROLE_CUSTOMER;

        return UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .avatarUrl(user.getAvatarUrl())
                .primaryRole(primaryRole)
                .roles(user.getRoles() != null
                        ? user.getRoles().stream().map(Role::getName).collect(Collectors.toSet())
                        : Collections.emptySet())
                .active(user.isActive())
                .verified(user.isVerified())
                .createdAt(user.getCreatedAt())
                .build();
    }

    public VehicleDto toVehicleDto(Vehicle vehicle) {
        if (vehicle == null) return null;

        List<String> imageUrls = vehicle.getImages() != null
                ? vehicle.getImages().stream().map(VehicleImage::getImageUrl).collect(Collectors.toList())
                : Collections.emptyList();

        return VehicleDto.builder()
                .id(vehicle.getId())
                .registrationNumber(vehicle.getRegistrationNumber())
                .vin(vehicle.getVin())
                .brand(vehicle.getBrand())
                .model(vehicle.getModel())
                .year(vehicle.getYear())
                .vehicleType(vehicle.getVehicleType())
                .fuelType(vehicle.getFuelType())
                .transmission(vehicle.getTransmission())
                .seatingCapacity(vehicle.getSeatingCapacity())
                .mileage(vehicle.getMileage())
                .batteryCapacityKwh(vehicle.getBatteryCapacityKwh())
                .fuelTankCapacityL(vehicle.getFuelTankCapacityL())
                .rentalPricePerDay(vehicle.getRentalPricePerDay())
                .rentalPricePerHour(vehicle.getRentalPricePerHour())
                .description(vehicle.getDescription())
                .location(vehicle.getLocation())
                .latitude(vehicle.getLatitude())
                .longitude(vehicle.getLongitude())
                .status(vehicle.getStatus())
                .available(vehicle.isAvailable())
                .currentOdometerKm(vehicle.getCurrentOdometerKm())
                .primaryImageUrl(vehicle.getPrimaryImageUrl())
                .imageUrls(imageUrls)
                .categoryId(vehicle.getCategory() != null ? vehicle.getCategory().getId() : null)
                .categoryName(vehicle.getCategory() != null ? vehicle.getCategory().getName() : null)
                .averageRating(vehicle.getAverageRating())
                .totalRatings(vehicle.getTotalRatings())
                .createdAt(vehicle.getCreatedAt())
                .build();
    }

    public DriverTrustScoreDto toDriverTrustScoreDto(DriverTrustScore score) {
        if (score == null) return null;
        return DriverTrustScoreDto.builder()
                .overallScore(score.getOverallScore())
                .trustLevel(score.getTrustLevel())
                .verificationFactor(score.getVerificationFactor())
                .experienceFactor(score.getExperienceFactor())
                .ratingFactor(score.getRatingFactor())
                .completionFactor(score.getCompletionFactor())
                .incidentPenalty(score.getIncidentPenalty())
                .explanation(score.getExplanation())
                .lastCalculatedAt(score.getLastCalculatedAt())
                .build();
    }

    public DriverVerificationDto toDriverVerificationDto(DriverVerification v) {
        if (v == null) return null;
        return DriverVerificationDto.builder()
                .id(v.getId())
                .driverId(v.getDriver() != null ? v.getDriver().getId() : null)
                .driverName(v.getDriver() != null && v.getDriver().getUser() != null
                        ? v.getDriver().getUser().getFullName() : null)
                .identityDocUrl(v.getIdentityDocUrl())
                .licenseDocUrl(v.getLicenseDocUrl())
                .policeVerificationDocUrl(v.getPoliceVerificationDocUrl())
                .medicalFitnessDocUrl(v.getMedicalFitnessDocUrl())
                .eyeTestDocUrl(v.getEyeTestDocUrl())
                .drivingSkillScore(v.getDrivingSkillScore())
                .status(v.getStatus())
                .verifiedByName(v.getVerifiedBy() != null ? v.getVerifiedBy().getFullName() : null)
                .adminNotes(v.getAdminNotes())
                .submittedAt(v.getSubmittedAt())
                .verifiedAt(v.getVerifiedAt())
                .build();
    }

    public DriverDto toDriverDto(Driver driver) {
        if (driver == null) return null;

        User user = driver.getUser();
        String fullName = user != null ? user.getFullName() : "Unknown Driver";
        String email = user != null ? user.getEmail() : "";
        String phone = user != null ? user.getPhoneNumber() : "";

        VerificationStatus vStatus = driver.getVerification() != null
                ? driver.getVerification().getStatus()
                : VerificationStatus.PENDING;

        return DriverDto.builder()
                .id(driver.getId())
                .userId(user != null ? user.getId() : null)
                .fullName(fullName)
                .email(email)
                .phoneNumber(phone)
                .licenseNumber(driver.getLicenseNumber())
                .licenseExpiryDate(driver.getLicenseExpiryDate())
                .experienceYears(driver.getExperienceYears())
                .rating(driver.getRating())
                .totalTrips(driver.getTotalTrips())
                .cancellationRate(driver.getCancellationRate())
                .available(driver.isAvailable())
                .dailyRate(driver.getDailyRate())
                .languages(driver.getLanguages())
                .address(driver.getAddress())
                .profilePhotoUrl(driver.getProfilePhotoUrl())
                .verificationStatus(vStatus)
                .trustScore(toDriverTrustScoreDto(driver.getTrustScore()))
                .currentVehicleId(driver.getCurrentVehicle() != null ? driver.getCurrentVehicle().getId() : null)
                .currentVehicleName(driver.getCurrentVehicle() != null
                        ? driver.getCurrentVehicle().getBrand() + " " + driver.getCurrentVehicle().getModel()
                        : null)
                .build();
    }

    public BookingDto toBookingDto(Booking booking) {
        if (booking == null) return null;

        return BookingDto.builder()
                .id(booking.getId())
                .bookingReference(booking.getBookingReference())
                .customer(toUserDto(booking.getCustomer()))
                .vehicle(toVehicleDto(booking.getVehicle()))
                .driver(toDriverDto(booking.getDriver()))
                .startDateTime(booking.getStartDateTime())
                .endDateTime(booking.getEndDateTime())
                .pickupLocation(booking.getPickupLocation())
                .dropLocation(booking.getDropLocation())
                .pickupLatitude(booking.getPickupLatitude())
                .pickupLongitude(booking.getPickupLongitude())
                .dropLatitude(booking.getDropLatitude())
                .dropLongitude(booking.getDropLongitude())
                .totalDays(booking.getTotalDays())
                .totalHours(booking.getTotalHours())
                .rentalAmount(booking.getRentalAmount())
                .driverAmount(booking.getDriverAmount())
                .taxAmount(booking.getTaxAmount())
                .totalAmount(booking.getTotalAmount())
                .bookingStatus(booking.getBookingStatus())
                .paymentStatus(booking.getPaymentStatus())
                .pickupTime(booking.getPickupTime())
                .returnTime(booking.getReturnTime())
                .notes(booking.getNotes())
                .createdAt(booking.getCreatedAt())
                .build();
    }

    public TripDto toTripDto(Trip trip) {
        if (trip == null) return null;

        BigDecimal remaining = trip.getDistanceKm() != null && trip.getDistanceTraveledKm() != null
                ? trip.getDistanceKm().subtract(trip.getDistanceTraveledKm()).max(BigDecimal.ZERO)
                : BigDecimal.ZERO;

        String vehName = trip.getBooking() != null && trip.getBooking().getVehicle() != null
                ? trip.getBooking().getVehicle().getBrand() + " " + trip.getBooking().getVehicle().getModel()
                : "Vehicle";

        Long vehId = trip.getBooking() != null && trip.getBooking().getVehicle() != null
                ? trip.getBooking().getVehicle().getId() : null;

        return TripDto.builder()
                .id(trip.getId())
                .bookingId(trip.getBooking() != null ? trip.getBooking().getId() : null)
                .bookingReference(trip.getBooking() != null ? trip.getBooking().getBookingReference() : null)
                .vehicleId(vehId)
                .vehicleName(vehName)
                .startLatitude(trip.getStartLatitude())
                .startLongitude(trip.getStartLongitude())
                .destLatitude(trip.getDestLatitude())
                .destLongitude(trip.getDestLongitude())
                .currentLatitude(trip.getCurrentLatitude())
                .currentLongitude(trip.getCurrentLongitude())
                .distanceKm(trip.getDistanceKm())
                .distanceTraveledKm(trip.getDistanceTraveledKm())
                .remainingDistanceKm(remaining)
                .status(trip.getStatus())
                .startTime(trip.getStartTime())
                .endTime(trip.getEndTime())
                .simulationStep(trip.getSimulationStep())
                .build();
    }

    public MaintenanceDto toMaintenanceDto(Maintenance m) {
        if (m == null) return null;

        String vehName = m.getVehicle() != null
                ? m.getVehicle().getBrand() + " " + m.getVehicle().getModel() : "Unknown";
        String reg = m.getVehicle() != null ? m.getVehicle().getRegistrationNumber() : "";

        return MaintenanceDto.builder()
                .id(m.getId())
                .vehicleId(m.getVehicle() != null ? m.getVehicle().getId() : null)
                .vehicleName(vehName)
                .registrationNumber(reg)
                .serviceType(m.getServiceType())
                .serviceDate(m.getServiceDate())
                .nextServiceDate(m.getNextServiceDate())
                .description(m.getDescription())
                .cost(m.getCost())
                .status(m.getStatus())
                .odometerKm(m.getOdometerKm())
                .notes(m.getNotes())
                .createdAt(m.getCreatedAt())
                .build();
    }

    public ReviewDto toReviewDto(Review r) {
        if (r == null) return null;

        String custName = r.getCustomer() != null ? r.getCustomer().getFullName() : "Customer";
        String vehName = r.getVehicle() != null
                ? r.getVehicle().getBrand() + " " + r.getVehicle().getModel() : "Vehicle";
        String driverName = r.getDriver() != null && r.getDriver().getUser() != null
                ? r.getDriver().getUser().getFullName() : null;

        return ReviewDto.builder()
                .id(r.getId())
                .bookingId(r.getBooking() != null ? r.getBooking().getId() : null)
                .bookingReference(r.getBooking() != null ? r.getBooking().getBookingReference() : null)
                .customerId(r.getCustomer() != null ? r.getCustomer().getId() : null)
                .customerName(custName)
                .vehicleId(r.getVehicle() != null ? r.getVehicle().getId() : null)
                .vehicleName(vehName)
                .driverId(r.getDriver() != null ? r.getDriver().getId() : null)
                .driverName(driverName)
                .rating(r.getRating())
                .vehicleRating(r.getVehicleRating())
                .driverRating(r.getDriverRating())
                .comment(r.getComment())
                .createdAt(r.getCreatedAt())
                .build();
    }

    public NotificationDto toNotificationDto(Notification n) {
        if (n == null) return null;
        return NotificationDto.builder()
                .id(n.getId())
                .title(n.getTitle())
                .message(n.getMessage())
                .type(n.getType())
                .read(n.isRead())
                .referenceType(n.getReferenceType())
                .referenceId(n.getReferenceId())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
