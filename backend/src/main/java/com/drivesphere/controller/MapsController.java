package com.drivesphere.controller;

import com.drivesphere.dto.ApiResponse;
import com.drivesphere.dto.TripPlannerResponse.PoiDto;
import com.drivesphere.integration.maps.MapsClient;
import com.drivesphere.integration.maps.MapsClient.RouteCalculationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/maps")
@RequiredArgsConstructor
public class MapsController {

    private final MapsClient mapsClient;

    @PostMapping("/route")
    public ResponseEntity<ApiResponse<RouteCalculationResult>> calculateRoute(
            @RequestParam double startLat,
            @RequestParam double startLng,
            @RequestParam double destLat,
            @RequestParam double destLng) {
        validateCoordinates(startLat, startLng);
        validateCoordinates(destLat, destLng);
        return ResponseEntity.ok(ApiResponse.success(
                mapsClient.calculateRoute(startLat, startLng, destLat, destLng),
                "Route calculation complete"
        ));
    }

    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<List<PoiDto>>> getNearby(
            @RequestParam double lat,
            @RequestParam double lng) {
        validateCoordinates(lat, lng);
        return ResponseEntity.ok(ApiResponse.success(
                mapsClient.getNearbyPlaces(lat, lng),
                "Nearby POI points of interest"
        ));
    }

    private void validateCoordinates(double lat, double lng) {
        if (lat < -90.0 || lat > 90.0) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90 degrees.");
        }
        if (lng < -180.0 || lng > 180.0) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180 degrees.");
        }
    }
}
