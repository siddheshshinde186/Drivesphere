package com.drivesphere.service;

import com.drivesphere.dto.GpsUpdateDto;
import com.drivesphere.dto.TripDto;
import com.drivesphere.entity.*;
import com.drivesphere.exception.ResourceNotFoundException;
import com.drivesphere.mapper.EntityDtoMapper;
import com.drivesphere.repository.GpsLogRepository;
import com.drivesphere.repository.TripRepository;
import com.drivesphere.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripTrackingService {

    private final TripRepository tripRepository;
    private final GpsLogRepository gpsLogRepository;
    private final VehicleRepository vehicleRepository;
    private final EntityDtoMapper mapper;

    @org.springframework.beans.factory.annotation.Value("${drivesphere.gps.allow-simulation:false}")
    private boolean allowSimulation;

    @Transactional(readOnly = true)
    public TripDto getTripByBookingId(Long bookingId) {
        return getTripByBookingId(bookingId, null, true);
    }

    @Transactional(readOnly = true)
    public TripDto getTripByBookingId(Long bookingId, Long currentUserId, boolean isStaff) {
        Trip trip = tripRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found for booking id: " + bookingId));

        if (!isStaff && currentUserId != null) {
            boolean isDriver = trip.getBooking() != null && trip.getBooking().getDriver() != null
                    && trip.getBooking().getDriver().getUser() != null
                    && trip.getBooking().getDriver().getUser().getId().equals(currentUserId);
            boolean isCustomer = trip.getBooking() != null && trip.getBooking().getCustomer() != null
                    && trip.getBooking().getCustomer().getId().equals(currentUserId);

            if (!isDriver && !isCustomer) {
                throw new AccessDeniedException("Access denied: You cannot view trip tracking for this booking.");
            }
        }

        return enrichTripDto(trip);
    }

    @Transactional(readOnly = true)
    public TripDto getTripById(Long tripId) {
        return getTripById(tripId, null, true);
    }

    @Transactional(readOnly = true)
    public TripDto getTripById(Long tripId, Long currentUserId, boolean isStaff) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found with id: " + tripId));

        if (!isStaff && currentUserId != null) {
            boolean isDriver = trip.getBooking() != null && trip.getBooking().getDriver() != null
                    && trip.getBooking().getDriver().getUser() != null
                    && trip.getBooking().getDriver().getUser().getId().equals(currentUserId);
            boolean isCustomer = trip.getBooking() != null && trip.getBooking().getCustomer() != null
                    && trip.getBooking().getCustomer().getId().equals(currentUserId);

            if (!isDriver && !isCustomer) {
                throw new AccessDeniedException("Access denied: You cannot view this trip.");
            }
        }

        return enrichTripDto(trip);
    }

    @Transactional
    public TripDto updateGpsLocation(GpsUpdateDto dto, Long currentUserId, boolean isStaff) {
        if (dto.getTripId() == null) {
            throw new IllegalArgumentException("Trip ID is required.");
        }
        if (dto.getLatitude() == null || dto.getLongitude() == null) {
            throw new IllegalArgumentException("Latitude and Longitude are required.");
        }
        double lat = dto.getLatitude().doubleValue();
        double lng = dto.getLongitude().doubleValue();
        if (lat < -90.0 || lat > 90.0) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90 degrees.");
        }
        if (lng < -180.0 || lng > 180.0) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180 degrees.");
        }
        if (dto.getSpeedKmh() != null && dto.getSpeedKmh().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Speed cannot be negative.");
        }

        Trip trip = tripRepository.findById(dto.getTripId())
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found with id: " + dto.getTripId()));

        if (trip.getStatus() == TripStatus.COMPLETED || (trip.getBooking() != null && trip.getBooking().getBookingStatus() == BookingStatus.CANCELLED)) {
            throw new IllegalStateException("Cannot update location for a completed or cancelled trip.");
        }

        if (!isStaff && currentUserId != null) {
            boolean isDriver = trip.getBooking() != null && trip.getBooking().getDriver() != null
                    && trip.getBooking().getDriver().getUser() != null
                    && trip.getBooking().getDriver().getUser().getId().equals(currentUserId);
            boolean isCustomer = trip.getBooking() != null && trip.getBooking().getCustomer() != null
                    && trip.getBooking().getCustomer().getId().equals(currentUserId);

            if (!isDriver && !isCustomer) {
                throw new AccessDeniedException("Unauthorized: You cannot broadcast GPS telemetry for another user's trip.");
            }
        }

        trip.setCurrentLatitude(dto.getLatitude());
        trip.setCurrentLongitude(dto.getLongitude());

        Vehicle vehicle = trip.getBooking() != null ? trip.getBooking().getVehicle() : null;
        if (vehicle != null) {
            vehicle.setLatitude(dto.getLatitude());
            vehicle.setLongitude(dto.getLongitude());
            vehicleRepository.save(vehicle);

            GpsLog logEntry = GpsLog.builder()
                    .trip(trip)
                    .vehicle(vehicle)
                    .latitude(dto.getLatitude())
                    .longitude(dto.getLongitude())
                    .speedKmh(dto.getSpeedKmh())
                    .headingDegrees(dto.getHeadingDegrees())
                    .batteryLevelPercent(dto.getBatteryLevelPercent())
                    .fuelLevelPercent(dto.getFuelLevelPercent())
                    .recordedAt(Instant.now())
                    .build();
            gpsLogRepository.save(logEntry);
        }

        Trip saved = tripRepository.save(trip);
        TripDto tripDto = mapper.toTripDto(saved);
        tripDto.setIsSimulatedTelemetry(false);
        tripDto.setTelemetrySource("DEVICE_HARDWARE_TELEMETRY");
        return tripDto;
    }

    /**
     * Advances simulated GPS telemetry step by step along the route for academic demonstration.
     * Fails fast in production if allow-simulation is set to false.
     */
    @Transactional
    public TripDto simulateTripStep(Long tripId) {
        return simulateTripStep(tripId, null, true);
    }

    @Transactional
    public TripDto simulateTripStep(Long tripId, Long currentUserId, boolean isStaff) {
        if (!allowSimulation) {
            throw new IllegalStateException("Simulated GPS telemetry is strictly disabled in production. Connect live GPS telemetry.");
        }

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found with id: " + tripId));

        if (!isStaff && currentUserId != null) {
            boolean isDriver = trip.getBooking() != null && trip.getBooking().getDriver() != null
                    && trip.getBooking().getDriver().getUser() != null
                    && trip.getBooking().getDriver().getUser().getId().equals(currentUserId);
            boolean isCustomer = trip.getBooking() != null && trip.getBooking().getCustomer() != null
                    && trip.getBooking().getCustomer().getId().equals(currentUserId);

            if (!isDriver && !isCustomer) {
                throw new AccessDeniedException("Unauthorized to simulate this trip.");
            }
        }

        if (trip.getStatus() == TripStatus.COMPLETED || (trip.getBooking() != null && trip.getBooking().getBookingStatus() == BookingStatus.CANCELLED)) {
            throw new IllegalStateException("Cannot simulate completed or cancelled trip.");
        }

        if (trip.getStatus() == TripStatus.NOT_STARTED) {
            trip.setStatus(TripStatus.IN_PROGRESS);
            trip.setStartTime(LocalDateTime.now());
        }

        int currentStep = trip.getSimulationStep() != null ? trip.getSimulationStep() : 0;
        int totalSteps = 20;

        if (currentStep < totalSteps) {
            currentStep++;
            trip.setSimulationStep(currentStep);

            double progress = (double) currentStep / totalSteps;

            double startLat = trip.getStartLatitude().doubleValue();
            double startLng = trip.getStartLongitude().doubleValue();
            double destLat = trip.getDestLatitude().doubleValue();
            double destLng = trip.getDestLongitude().doubleValue();

            // Interpolate position with slight realistic road deviation
            double currentLat = startLat + progress * (destLat - startLat) + Math.sin(progress * Math.PI) * 0.008;
            double currentLng = startLng + progress * (destLng - startLng) + Math.cos(progress * Math.PI) * 0.005;

            BigDecimal latBd = BigDecimal.valueOf(currentLat).setScale(6, RoundingMode.HALF_UP);
            BigDecimal lngBd = BigDecimal.valueOf(currentLng).setScale(6, RoundingMode.HALF_UP);

            trip.setCurrentLatitude(latBd);
            trip.setCurrentLongitude(lngBd);

            BigDecimal totalDist = trip.getDistanceKm() != null ? trip.getDistanceKm() : BigDecimal.valueOf(100.0);
            BigDecimal traveled = totalDist.multiply(BigDecimal.valueOf(progress)).setScale(2, RoundingMode.HALF_UP);
            trip.setDistanceTraveledKm(traveled);

            Vehicle vehicle = trip.getBooking() != null ? trip.getBooking().getVehicle() : null;
            if (vehicle != null) {
                vehicle.setLatitude(latBd);
                vehicle.setLongitude(lngBd);
                vehicleRepository.save(vehicle);

                GpsLog logEntry = GpsLog.builder()
                        .trip(trip)
                        .vehicle(vehicle)
                        .latitude(latBd)
                        .longitude(lngBd)
                        .speedKmh(BigDecimal.valueOf(52.0 + Math.random() * 15.0).setScale(1, RoundingMode.HALF_UP))
                        .headingDegrees(BigDecimal.valueOf(135.0))
                        .batteryLevelPercent(BigDecimal.valueOf(Math.max(15.0, 90.0 - progress * 40.0)).setScale(1, RoundingMode.HALF_UP))
                        .fuelLevelPercent(BigDecimal.valueOf(Math.max(20.0, 85.0 - progress * 35.0)).setScale(1, RoundingMode.HALF_UP))
                        .recordedAt(Instant.now())
                        .build();
                gpsLogRepository.save(logEntry);
            }

            if (currentStep >= totalSteps) {
                trip.setStatus(TripStatus.COMPLETED);
                trip.setEndTime(LocalDateTime.now());
            }
        }

        Trip saved = tripRepository.save(trip);
        TripDto tripDto = mapper.toTripDto(saved);
        tripDto.setIsSimulatedTelemetry(true);
        tripDto.setTelemetrySource("ACADEMIC_ROUTE_INTERPOLATOR");
        return tripDto;
    }

    private TripDto enrichTripDto(Trip trip) {
        TripDto dto = mapper.toTripDto(trip);
        dto.setIsSimulatedTelemetry(trip.getSimulationStep() != null && trip.getSimulationStep() > 0);
        dto.setTelemetrySource(dto.getIsSimulatedTelemetry() ? "ACADEMIC_ROUTE_INTERPOLATOR" : "DEVICE_HARDWARE_TELEMETRY");

        // Enrich with genuine latest telemetry recorded for this trip if available
        gpsLogRepository.findFirstByTripIdOrderByRecordedAtDesc(trip.getId())
                .ifPresent(log -> {
                    dto.setSpeedKmh(log.getSpeedKmh());
                    dto.setHeadingDegrees(log.getHeadingDegrees());
                    dto.setBatteryLevelPercent(log.getBatteryLevelPercent());
                    dto.setFuelLevelPercent(log.getFuelLevelPercent());
                });

        return dto;
    }
}
