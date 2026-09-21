package com.drivesphere.controller;

import com.drivesphere.dto.ApiResponse;
import com.drivesphere.dto.DriverDto;
import com.drivesphere.dto.UserDto;
import com.drivesphere.service.AuthService;
import com.drivesphere.service.DriverService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/drivers")
@RequiredArgsConstructor
public class DriverController {

    private final DriverService driverService;
    private final AuthService authService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DriverDto>>> getAllDrivers() {
        return ResponseEntity.ok(ApiResponse.success(driverService.getAllDrivers(), "All drivers"));
    }

    @GetMapping("/available")
    public ResponseEntity<ApiResponse<List<DriverDto>>> getAvailableDrivers() {
        return ResponseEntity.ok(ApiResponse.success(
                driverService.getAvailableAndApprovedDrivers(),
                "Available and verified drivers for customer hiring"
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DriverDto>> getDriverById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(driverService.getDriverById(id), "Driver profile"));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<DriverDto>> getMyDriverProfile() {
        UserDto currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Unauthorized"));
        }
        return ResponseEntity.ok(ApiResponse.success(
                driverService.getDriverByUserId(currentUser.getId()),
                "My driver profile"
        ));
    }

    @PutMapping("/{id}/availability")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN', 'FLEET_MANAGER')")
    public ResponseEntity<ApiResponse<DriverDto>> toggleAvailability(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                driverService.toggleAvailability(id),
                "Driver availability updated"
        ));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')")
    public ResponseEntity<ApiResponse<DriverDto>> updateProfile(@PathVariable Long id, @RequestBody DriverDto dto) {
        return ResponseEntity.ok(ApiResponse.success(
                driverService.updateProfile(id, dto),
                "Driver profile updated"
        ));
    }
}
