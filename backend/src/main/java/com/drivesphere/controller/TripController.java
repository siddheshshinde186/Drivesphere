package com.drivesphere.controller;

import com.drivesphere.dto.ApiResponse;
import com.drivesphere.dto.GpsUpdateDto;
import com.drivesphere.dto.TripDto;
import com.drivesphere.service.TripTrackingService;
import com.drivesphere.dto.UserDto;
import com.drivesphere.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripTrackingService tripService;
    private final AuthService authService;

    @GetMapping("/booking/{bookingId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TripDto>> getTripByBookingId(@PathVariable Long bookingId) {
        UserDto currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Unauthorized"));
        }
        boolean isStaff = currentUser.isStaff();

        return ResponseEntity.ok(ApiResponse.success(
                tripService.getTripByBookingId(bookingId, currentUser.getId(), isStaff),
                "Trip tracking data"
        ));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TripDto>> getTripById(@PathVariable Long id) {
        UserDto currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Unauthorized"));
        }
        boolean isStaff = currentUser.isStaff();

        return ResponseEntity.ok(ApiResponse.success(
                tripService.getTripById(id, currentUser.getId(), isStaff),
                "Trip details"
        ));
    }

    @PostMapping("/location")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TripDto>> updateLocation(@Valid @RequestBody GpsUpdateDto dto) {
        UserDto currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Unauthorized"));
        }
        boolean isStaff = currentUser.isStaff();

        return ResponseEntity.ok(ApiResponse.success(
                tripService.updateGpsLocation(dto, currentUser.getId(), isStaff),
                "GPS location updated"
        ));
    }

    @PostMapping("/{id}/simulate")
    @PreAuthorize("hasAnyRole('ADMIN', 'FLEET_MANAGER', 'DRIVER', 'CUSTOMER') or hasAnyAuthority('ROLE_ADMIN', 'ROLE_FLEET_MANAGER', 'ROLE_DRIVER', 'ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<TripDto>> simulateStep(@PathVariable Long id) {
        UserDto currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Unauthorized"));
        }
        boolean isStaff = currentUser.isStaff();

        return ResponseEntity.ok(ApiResponse.success(
                tripService.simulateTripStep(id, currentUser.getId(), isStaff),
                "GPS telemetry advanced along route [DEMO SIMULATION]"
        ));
    }
}
