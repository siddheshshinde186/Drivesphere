package com.drivesphere.controller;

import com.drivesphere.dto.ApiResponse;
import com.drivesphere.dto.VehicleCreateUpdateRequest;
import com.drivesphere.dto.VehicleDto;
import com.drivesphere.entity.FuelType;
import com.drivesphere.entity.Transmission;
import com.drivesphere.entity.VehicleType;
import com.drivesphere.service.VehicleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<VehicleDto>>> getAllVehicles() {
        return ResponseEntity.ok(ApiResponse.success(vehicleService.getAllVehicles(), "Vehicles retrieved"));
    }

    @GetMapping("/available")
    public ResponseEntity<ApiResponse<List<VehicleDto>>> getAvailableVehicles() {
        return ResponseEntity.ok(ApiResponse.success(vehicleService.getAvailableVehicles(), "Available vehicles retrieved"));
    }

    @GetMapping("/available-dates")
    public ResponseEntity<ApiResponse<List<VehicleDto>>> getAvailableVehiclesForDates(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(ApiResponse.success(
                vehicleService.getAvailableVehiclesForPeriod(start, end),
                "Available vehicles for period retrieved"
        ));
    }

    @GetMapping("/filter")
    public ResponseEntity<ApiResponse<List<VehicleDto>>> filterVehicles(
            @RequestParam(required = false) VehicleType type,
            @RequestParam(required = false) FuelType fuel,
            @RequestParam(required = false) Transmission transmission,
            @RequestParam(required = false) Integer minSeats,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String brand) {
        return ResponseEntity.ok(ApiResponse.success(
                vehicleService.filterVehicles(type, fuel, transmission, minSeats, maxPrice, brand),
                "Filtered vehicles retrieved"
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VehicleDto>> getVehicleById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(vehicleService.getVehicleById(id), "Vehicle details"));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FLEET_MANAGER')")
    public ResponseEntity<ApiResponse<VehicleDto>> createVehicle(@Valid @RequestBody VehicleCreateUpdateRequest request) {
        VehicleDto created = vehicleService.createVehicle(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Vehicle created successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FLEET_MANAGER')")
    public ResponseEntity<ApiResponse<VehicleDto>> updateVehicle(@PathVariable Long id,
                                                                @Valid @RequestBody VehicleCreateUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(vehicleService.updateVehicle(id, request), "Vehicle updated"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FLEET_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> deleteVehicle(@PathVariable Long id) {
        vehicleService.deleteVehicle(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Vehicle deactivated successfully"));
    }
}
