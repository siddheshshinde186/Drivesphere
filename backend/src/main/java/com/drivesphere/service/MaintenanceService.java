package com.drivesphere.service;

import com.drivesphere.dto.MaintenanceCreateRequest;
import com.drivesphere.dto.MaintenanceDto;
import com.drivesphere.entity.Maintenance;
import com.drivesphere.entity.MaintenanceStatus;
import com.drivesphere.entity.Vehicle;
import com.drivesphere.entity.VehicleStatus;
import com.drivesphere.exception.ResourceNotFoundException;
import com.drivesphere.mapper.EntityDtoMapper;
import com.drivesphere.repository.MaintenanceRepository;
import com.drivesphere.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MaintenanceService {

    private final MaintenanceRepository maintenanceRepository;
    private final VehicleRepository vehicleRepository;
    private final EntityDtoMapper mapper;

    @Transactional(readOnly = true)
    public List<MaintenanceDto> getAllMaintenance() {
        return maintenanceRepository.findAll().stream()
                .map(mapper::toMaintenanceDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MaintenanceDto> getVehicleMaintenanceHistory(Long vehicleId) {
        return maintenanceRepository.findByVehicleIdOrderByServiceDateDesc(vehicleId).stream()
                .map(mapper::toMaintenanceDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MaintenanceDto> getUpcomingMaintenance() {
        return maintenanceRepository.findUpcomingMaintenance(LocalDate.now().plusDays(14)).stream()
                .map(mapper::toMaintenanceDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public MaintenanceDto scheduleMaintenance(MaintenanceCreateRequest request) {
        Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + request.getVehicleId()));

        Maintenance maintenance = Maintenance.builder()
                .vehicle(vehicle)
                .serviceType(request.getServiceType())
                .serviceDate(request.getServiceDate())
                .nextServiceDate(request.getNextServiceDate())
                .description(request.getDescription().trim())
                .cost(request.getCost())
                .status(MaintenanceStatus.SCHEDULED)
                .odometerKm(request.getOdometerKm() != null ? request.getOdometerKm() : vehicle.getCurrentOdometerKm())
                .notes(request.getNotes())
                .build();

        maintenance = maintenanceRepository.save(maintenance);

        // Mark vehicle as in MAINTENANCE so it cannot be rented
        vehicle.setStatus(VehicleStatus.MAINTENANCE);
        vehicle.setAvailable(false);
        vehicleRepository.save(vehicle);

        return mapper.toMaintenanceDto(maintenance);
    }

    @Transactional
    public MaintenanceDto updateMaintenanceStatus(Long id, MaintenanceStatus status, BigDecimal finalCost, String notes) {
        Maintenance maintenance = maintenanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Maintenance record not found with id: " + id));

        maintenance.setStatus(status);
        if (finalCost != null) maintenance.setCost(finalCost);
        if (notes != null) maintenance.setNotes(notes);

        if (status == MaintenanceStatus.COMPLETED) {
            Vehicle vehicle = maintenance.getVehicle();
            vehicle.setStatus(VehicleStatus.AVAILABLE);
            vehicle.setAvailable(true);
            if (maintenance.getOdometerKm() != null) {
                vehicle.setCurrentOdometerKm(maintenance.getOdometerKm());
            }
            vehicleRepository.save(vehicle);
        }

        return mapper.toMaintenanceDto(maintenanceRepository.save(maintenance));
    }
}
