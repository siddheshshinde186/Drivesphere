package com.drivesphere.service;

import com.drivesphere.dto.PublicVehicleDto;
import com.drivesphere.dto.VehicleCreateUpdateRequest;
import com.drivesphere.dto.VehicleDto;
import com.drivesphere.entity.*;
import com.drivesphere.exception.ResourceNotFoundException;
import com.drivesphere.mapper.EntityDtoMapper;
import com.drivesphere.repository.BookingRepository;
import com.drivesphere.repository.MaintenanceRepository;
import com.drivesphere.repository.VehicleCategoryRepository;
import com.drivesphere.repository.VehicleImageRepository;
import com.drivesphere.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final VehicleCategoryRepository categoryRepository;
    private final VehicleImageRepository vehicleImageRepository;
    private final EntityDtoMapper mapper;

    @Autowired(required = false)
    private BookingRepository bookingRepository;

    @Autowired(required = false)
    private MaintenanceRepository maintenanceRepository;

    @org.springframework.context.annotation.Lazy
    @Autowired(required = false)
    private MaintenanceService maintenanceService;

    public void setBookingRepository(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    public void setMaintenanceRepository(MaintenanceRepository maintenanceRepository) {
        this.maintenanceRepository = maintenanceRepository;
    }

    public void setMaintenanceService(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @Transactional(readOnly = true)
    public List<VehicleDto> getAllVehicles() {
        return vehicleRepository.findAll().stream()
                .map(mapper::toVehicleDto)
                .collect(Collectors.toList());
    }

    private boolean isVehicleRestrictedByMaintenance(Vehicle v, LocalDateTime start, LocalDateTime end) {
        if (v == null || v.getId() == null) return false;
        if (v.getStatus() == VehicleStatus.MAINTENANCE || v.getStatus() == VehicleStatus.INACTIVE) return true;

        if (maintenanceService != null) {
            if (maintenanceService.calculateServiceStatus(v) == com.drivesphere.entity.VehicleServiceStatus.OVERDUE) {
                return true;
            }
        }

        if (maintenanceRepository != null && start != null && end != null) {
            long maintConflicts = maintenanceRepository.countByVehicleIdAndStatusInAndServiceDateBetween(
                    v.getId(),
                    List.of(MaintenanceStatus.SCHEDULED, MaintenanceStatus.IN_PROGRESS),
                    start.toLocalDate(),
                    end.toLocalDate()
            );
            if (maintConflicts > 0) return true;
        }

        return false;
    }

    @Transactional(readOnly = true)
    public List<PublicVehicleDto> getAvailableVehicles() {
        return vehicleRepository.findOperationalAvailableVehicles().stream()
                .filter(v -> !isVehicleRestrictedByMaintenance(v, null, null))
                .map(mapper::toPublicVehicleDto)
                .collect(Collectors.toList());
    }

    /**
     * Customer-Visible Explore Fleet.
     * Returns all registered vehicles eligible for customer viewing (excluding only INACTIVE vehicles).
     * Displays actual operational status (AVAILABLE, BOOKED, RENTED, MAINTENANCE).
     */
    @Transactional(readOnly = true)
    public List<PublicVehicleDto> getCustomerVisibleFleet() {
        return vehicleRepository.findCustomerVisibleFleet().stream()
                .map(mapper::toPublicVehicleDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PublicVehicleDto> getAvailableVehiclesForPeriod(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Both start and end dates must be provided.");
        }
        if (end.isBefore(start) || end.isEqual(start)) {
            throw new IllegalArgumentException("End date-time must be strictly after start date-time.");
        }
        return vehicleRepository.findAvailableVehiclesForPeriod(start, end).stream()
                .filter(v -> !isVehicleRestrictedByMaintenance(v, start, end))
                .map(mapper::toPublicVehicleDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public VehicleDto getVehicleById(Long id) {
        return vehicleRepository.findById(id)
                .map(mapper::toVehicleDto)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public PublicVehicleDto getPublicVehicleById(Long id) {
        return vehicleRepository.findById(id)
                .map(mapper::toPublicVehicleDto)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public List<PublicVehicleDto> filterVehicles(VehicleType type, FuelType fuel, Transmission trans,
                                          Integer seats, BigDecimal maxPrice, String brand) {
        return filterVehicles(type, fuel, trans, seats, maxPrice, brand, null, null);
    }

    @Transactional(readOnly = true)
    public List<PublicVehicleDto> filterVehicles(VehicleType type, FuelType fuel, Transmission trans,
                                          Integer seats, BigDecimal maxPrice, String brand,
                                          LocalDateTime startDateTime, LocalDateTime endDateTime) {
        if ((startDateTime != null && endDateTime == null) || (startDateTime == null && endDateTime != null)) {
            throw new IllegalArgumentException("Both startDateTime and endDateTime must be provided for rental period filtering.");
        }
        if (startDateTime != null && endDateTime != null) {
            if (endDateTime.isBefore(startDateTime) || endDateTime.isEqual(startDateTime)) {
                throw new IllegalArgumentException("endDateTime must be strictly after startDateTime.");
            }
        }
        String cleanBrand = (brand != null && !brand.isBlank()) ? brand.trim() : null;
        return vehicleRepository.filterVehicles(type, fuel, trans, seats, maxPrice, cleanBrand, startDateTime, endDateTime).stream()
                .filter(v -> {
                    if (startDateTime != null && endDateTime != null) {
                        return !isVehicleRestrictedByMaintenance(v, startDateTime, endDateTime);
                    }
                    return v.getStatus() != VehicleStatus.INACTIVE;
                })
                .map(mapper::toPublicVehicleDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public VehicleDto createVehicle(VehicleCreateUpdateRequest request) {
        if (vehicleRepository.findByRegistrationNumber(request.getRegistrationNumber()).isPresent()) {
            throw new IllegalArgumentException("Registration number already exists: " + request.getRegistrationNumber());
        }

        VehicleCategory category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId()).orElse(null);
        }

        Vehicle vehicle = Vehicle.builder()
                .registrationNumber(request.getRegistrationNumber().trim().toUpperCase())
                .vin(request.getVin())
                .brand(request.getBrand().trim())
                .model(request.getModel().trim())
                .year(request.getYear())
                .vehicleType(request.getVehicleType())
                .fuelType(request.getFuelType())
                .transmission(request.getTransmission())
                .seatingCapacity(request.getSeatingCapacity())
                .mileage(request.getMileage())
                .batteryCapacityKwh(request.getBatteryCapacityKwh())
                .fuelTankCapacityL(request.getFuelTankCapacityL())
                .rentalPricePerDay(request.getRentalPricePerDay())
                .rentalPricePerHour(request.getRentalPricePerHour())
                .description(request.getDescription())
                .location(request.getLocation() != null && !request.getLocation().isBlank() ? request.getLocation().trim() : null)
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .status(request.getStatus() != null ? request.getStatus() : VehicleStatus.AVAILABLE)
                .available(request.getAvailable() != null ? request.getAvailable() : true)
                .currentOdometerKm(request.getCurrentOdometerKm() != null ? request.getCurrentOdometerKm() : BigDecimal.ZERO)
                .primaryImageUrl(request.getPrimaryImageUrl())
                .category(category)
                .averageRating(BigDecimal.ZERO)
                .totalRatings(0)
                .build();

        vehicle = vehicleRepository.save(vehicle);

        if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
            for (String imgUrl : request.getImageUrls()) {
                VehicleImage img = VehicleImage.builder()
                        .vehicle(vehicle)
                        .imageUrl(imgUrl)
                        .primary(imgUrl.equals(vehicle.getPrimaryImageUrl()))
                        .build();
                vehicleImageRepository.save(img);
            }
        }

        return mapper.toVehicleDto(vehicle);
    }

    @Transactional
    public VehicleDto updateVehicle(Long id, VehicleCreateUpdateRequest request) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + id));

        vehicle.setBrand(request.getBrand().trim());
        vehicle.setModel(request.getModel().trim());
        vehicle.setYear(request.getYear());
        vehicle.setVehicleType(request.getVehicleType());
        vehicle.setFuelType(request.getFuelType());
        vehicle.setTransmission(request.getTransmission());
        vehicle.setSeatingCapacity(request.getSeatingCapacity());
        vehicle.setMileage(request.getMileage());
        vehicle.setRentalPricePerDay(request.getRentalPricePerDay());
        vehicle.setRentalPricePerHour(request.getRentalPricePerHour());
        vehicle.setDescription(request.getDescription());
        if (request.getLocation() != null) vehicle.setLocation(request.getLocation());
        if (request.getLatitude() != null) vehicle.setLatitude(request.getLatitude());
        if (request.getLongitude() != null) vehicle.setLongitude(request.getLongitude());
        if (request.getStatus() != null) vehicle.setStatus(request.getStatus());
        if (request.getAvailable() != null) vehicle.setAvailable(request.getAvailable());
        if (request.getPrimaryImageUrl() != null) vehicle.setPrimaryImageUrl(request.getPrimaryImageUrl());

        return mapper.toVehicleDto(vehicleRepository.save(vehicle));
    }

    @Transactional
    public VehicleDto addVehicleImage(Long vehicleId, String imageUrl, boolean isPrimary) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + vehicleId));

        if (isPrimary || vehicle.getPrimaryImageUrl() == null || vehicle.getPrimaryImageUrl().isBlank()) {
            vehicle.setPrimaryImageUrl(imageUrl);
        }

        if (vehicleImageRepository != null && imageUrl != null && !imageUrl.isBlank()) {
            VehicleImage img = VehicleImage.builder()
                    .vehicle(vehicle)
                    .imageUrl(imageUrl)
                    .primary(isPrimary)
                    .build();
            vehicleImageRepository.save(img);
        }

        return mapper.toVehicleDto(vehicleRepository.save(vehicle));
    }

    @Transactional
    public void deleteVehicle(Long id) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + id));
        vehicle.setStatus(VehicleStatus.INACTIVE);
        vehicle.setAvailable(false);
        vehicleRepository.save(vehicle);
    }

    /**
     * Centralized Operational Status Reconciliation.
     * Prevents contradictory state transitions between booking cancellation,
     * hold expiry, maintenance scheduling/completion, and rental returns.
     *
     * Validates:
     * 1. Vehicle decommissioning and inactive administrative status.
     * 2. In-progress or scheduled maintenance due today or earlier.
     * 3. Concurrent active bookings, in-progress rentals, or valid booking holds.
     * 4. Restores to AVAILABLE only if all operational invariants are satisfied.
     */
    @Transactional
    public Vehicle reconcileVehicleOperationalStatus(Vehicle vehicle) {
        if (vehicle == null || vehicle.getId() == null) {
            return vehicle;
        }

        // 1. Inactive or decommissioned operational restrictions
        if (vehicle.getStatus() == VehicleStatus.INACTIVE) {
            vehicle.setAvailable(false);
            return vehicleRepository.save(vehicle);
        }

        // 2. Scheduled or in-progress maintenance checks
        if (maintenanceRepository != null) {
            List<Maintenance> inProgress = maintenanceRepository.findByVehicleIdAndStatus(
                    vehicle.getId(), MaintenanceStatus.IN_PROGRESS);
            if (!inProgress.isEmpty()) {
                vehicle.setStatus(VehicleStatus.MAINTENANCE);
                vehicle.setAvailable(false);
                return vehicleRepository.save(vehicle);
            }

            List<Maintenance> scheduled = maintenanceRepository.findByVehicleIdAndStatus(
                    vehicle.getId(), MaintenanceStatus.SCHEDULED);
            LocalDate today = LocalDate.now();
            boolean maintenanceDue = scheduled.stream()
                    .anyMatch(m -> m.getServiceDate() != null && !m.getServiceDate().isAfter(today));
            if (maintenanceDue) {
                vehicle.setStatus(VehicleStatus.MAINTENANCE);
                vehicle.setAvailable(false);
                return vehicleRepository.save(vehicle);
            }
        }

        if (maintenanceService != null && maintenanceService.calculateServiceStatus(vehicle) == com.drivesphere.entity.VehicleServiceStatus.OVERDUE) {
            vehicle.setStatus(VehicleStatus.MAINTENANCE);
            vehicle.setAvailable(false);
            return vehicleRepository.save(vehicle);
        }

        // 3. Active bookings and ongoing rental checks
        if (bookingRepository != null) {
            // Ongoing active rental
            List<Booking> activeRentals = bookingRepository.findByVehicleIdAndBookingStatusIn(
                    vehicle.getId(), List.of(BookingStatus.ACTIVE));
            if (!activeRentals.isEmpty()) {
                vehicle.setStatus(VehicleStatus.RENTED);
                vehicle.setAvailable(false);
                return vehicleRepository.save(vehicle);
            }

            LocalDateTime now = LocalDateTime.now();
            List<Booking> confirmedOrHeld = bookingRepository.findByVehicleIdAndBookingStatusIn(
                    vehicle.getId(), List.of(BookingStatus.CONFIRMED, BookingStatus.HELD, BookingStatus.PENDING));
            boolean currentlyBooked = confirmedOrHeld.stream().anyMatch(b -> {
                if (b.getBookingStatus() == BookingStatus.HELD) {
                    return b.getHoldExpiresAt() != null && b.getHoldExpiresAt().isAfter(now);
                }
                if (b.getStartDateTime() != null && b.getEndDateTime() != null) {
                    return !now.isBefore(b.getStartDateTime()) && !now.isAfter(b.getEndDateTime());
                }
                return false;
            });

            if (currentlyBooked) {
                vehicle.setStatus(VehicleStatus.BOOKED);
                vehicle.setAvailable(false);
                return vehicleRepository.save(vehicle);
            }
        }

        // 4. All invariants satisfied -> Genuine operational availability
        vehicle.setStatus(VehicleStatus.AVAILABLE);
        vehicle.setAvailable(true);
        return vehicleRepository.save(vehicle);
    }

    @Transactional
    public VehicleDto toggleVehicleStatus(Long id) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + id));
        if (vehicle.getStatus() == VehicleStatus.INACTIVE) {
            vehicle.setStatus(VehicleStatus.AVAILABLE);
            vehicle.setAvailable(true);
            return mapper.toVehicleDto(reconcileVehicleOperationalStatus(vehicle));
        } else {
            vehicle.setStatus(VehicleStatus.INACTIVE);
            vehicle.setAvailable(false);
            return mapper.toVehicleDto(vehicleRepository.save(vehicle));
        }
    }
}
