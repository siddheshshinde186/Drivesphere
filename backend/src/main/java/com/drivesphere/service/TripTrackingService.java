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

    @Transactional(readOnly = true)
    public TripDto getTripByBookingId(Long bookingId) {
        return tripRepository.findByBookingId(bookingId)
                .map(mapper::toTripDto)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found for booking id: " + bookingId));
    }

    @Transactional(readOnly = true)
    public TripDto getTripById(Long tripId) {
        return tripRepository.findById(tripId)
                .map(mapper::toTripDto)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found with id: " + tripId));
    }

    @Transactional
    public TripDto updateGpsLocation(GpsUpdateDto dto) {
        Trip trip = tripRepository.findById(dto.getTripId())
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found with id: " + dto.getTripId()));

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
                    .speedKmh(dto.getSpeedKmh() != null ? dto.getSpeedKmh() : BigDecimal.valueOf(48.5))
                    .headingDegrees(dto.getHeadingDegrees() != null ? dto.getHeadingDegrees() : BigDecimal.valueOf(180.0))
                    .batteryLevelPercent(dto.getBatteryLevelPercent() != null ? dto.getBatteryLevelPercent() : BigDecimal.valueOf(82.0))
                    .fuelLevelPercent(dto.getFuelLevelPercent() != null ? dto.getFuelLevelPercent() : BigDecimal.valueOf(74.0))
                    .recordedAt(Instant.now())
                    .build();
            gpsLogRepository.save(logEntry);
        }

        return mapper.toTripDto(tripRepository.save(trip));
    }

    /**
     * Advances simulated GPS telemetry step by step along the route for academic demonstration.
     */
    @Transactional
    public TripDto simulateTripStep(Long tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found with id: " + tripId));

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

        return mapper.toTripDto(tripRepository.save(trip));
    }
}
