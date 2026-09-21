package com.drivesphere.service;

import com.drivesphere.dto.VehicleCreateUpdateRequest;
import com.drivesphere.dto.VehicleDto;
import com.drivesphere.entity.*;
import com.drivesphere.exception.ResourceNotFoundException;
import com.drivesphere.mapper.EntityDtoMapper;
import com.drivesphere.repository.VehicleCategoryRepository;
import com.drivesphere.repository.VehicleImageRepository;
import com.drivesphere.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    @Transactional(readOnly = true)
    public List<VehicleDto> getAllVehicles() {
        return vehicleRepository.findAll().stream()
                .map(mapper::toVehicleDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<VehicleDto> getAvailableVehicles() {
        return vehicleRepository.findOperationalAvailableVehicles().stream()
                .map(mapper::toVehicleDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<VehicleDto> getAvailableVehiclesForPeriod(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return getAvailableVehicles();
        }
        return vehicleRepository.findAvailableVehiclesForPeriod(start, end).stream()
                .map(mapper::toVehicleDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public VehicleDto getVehicleById(Long id) {
        return vehicleRepository.findById(id)
                .map(mapper::toVehicleDto)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public List<VehicleDto> filterVehicles(VehicleType type, FuelType fuel, Transmission trans,
                                          Integer seats, BigDecimal maxPrice, String brand) {
        return vehicleRepository.filterVehicles(type, fuel, trans, seats, maxPrice, brand).stream()
                .map(mapper::toVehicleDto)
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
                .location(request.getLocation() != null ? request.getLocation() : "Downtown Hub")
                .latitude(request.getLatitude() != null ? request.getLatitude() : BigDecimal.valueOf(12.9716))
                .longitude(request.getLongitude() != null ? request.getLongitude() : BigDecimal.valueOf(77.5946))
                .status(request.getStatus() != null ? request.getStatus() : VehicleStatus.AVAILABLE)
                .available(request.getAvailable() != null ? request.getAvailable() : true)
                .currentOdometerKm(request.getCurrentOdometerKm() != null ? request.getCurrentOdometerKm() : BigDecimal.valueOf(12000))
                .primaryImageUrl(request.getPrimaryImageUrl())
                .category(category)
                .averageRating(BigDecimal.valueOf(5.00))
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
    public void deleteVehicle(Long id) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + id));
        vehicle.setStatus(VehicleStatus.INACTIVE);
        vehicle.setAvailable(false);
        vehicleRepository.save(vehicle);
    }
}
