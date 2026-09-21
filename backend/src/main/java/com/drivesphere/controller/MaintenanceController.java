package com.drivesphere.controller;

import com.drivesphere.dto.ApiResponse;
import com.drivesphere.dto.MaintenanceCreateRequest;
import com.drivesphere.dto.MaintenanceDto;
import com.drivesphere.entity.MaintenanceStatus;
import com.drivesphere.service.MaintenanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/maintenance")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('FLEET_MANAGER', 'ADMIN')")
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<MaintenanceDto>>> getAllMaintenance() {
        return ResponseEntity.ok(ApiResponse.success(
                maintenanceService.getAllMaintenance(),
                "All maintenance records"
        ));
    }

    @GetMapping("/upcoming")
    public ResponseEntity<ApiResponse<List<MaintenanceDto>>> getUpcomingMaintenance() {
        return ResponseEntity.ok(ApiResponse.success(
                maintenanceService.getUpcomingMaintenance(),
                "Upcoming maintenance tasks"
        ));
    }

    @GetMapping("/vehicle/{vehicleId}")
    public ResponseEntity<ApiResponse<List<MaintenanceDto>>> getVehicleHistory(@PathVariable Long vehicleId) {
        return ResponseEntity.ok(ApiResponse.success(
                maintenanceService.getVehicleMaintenanceHistory(vehicleId),
                "Vehicle maintenance history"
        ));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MaintenanceDto>> scheduleMaintenance(
            @Valid @RequestBody MaintenanceCreateRequest request) {
        MaintenanceDto dto = maintenanceService.scheduleMaintenance(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(dto, "Maintenance scheduled. Vehicle locked as MAINTENANCE."));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<MaintenanceDto>> updateStatus(
            @PathVariable Long id,
            @RequestParam MaintenanceStatus status,
            @RequestParam(required = false) BigDecimal cost,
            @RequestParam(required = false) String notes) {
        return ResponseEntity.ok(ApiResponse.success(
                maintenanceService.updateMaintenanceStatus(id, status, cost, notes),
                "Maintenance record updated"
        ));
    }
}
