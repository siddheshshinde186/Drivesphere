package com.drivesphere.controller;

import com.drivesphere.dto.ApiResponse;
import com.drivesphere.dto.GpsUpdateDto;
import com.drivesphere.dto.TripDto;
import com.drivesphere.service.TripTrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripTrackingService tripService;

    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<ApiResponse<TripDto>> getTripByBookingId(@PathVariable Long bookingId) {
        return ResponseEntity.ok(ApiResponse.success(
                tripService.getTripByBookingId(bookingId),
                "Trip tracking data"
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TripDto>> getTripById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(tripService.getTripById(id), "Trip details"));
    }

    @PostMapping("/location")
    public ResponseEntity<ApiResponse<TripDto>> updateLocation(@RequestBody GpsUpdateDto dto) {
        return ResponseEntity.ok(ApiResponse.success(tripService.updateGpsLocation(dto), "GPS location updated"));
    }

    @PostMapping("/{id}/simulate")
    public ResponseEntity<ApiResponse<TripDto>> simulateStep(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                tripService.simulateTripStep(id),
                "GPS telemetry advanced along route"
        ));
    }
}
