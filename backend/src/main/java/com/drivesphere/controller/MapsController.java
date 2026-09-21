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
        return ResponseEntity.ok(ApiResponse.success(
                mapsClient.calculateRoute(startLat, startLng, destLat, destLng),
                "Route calculation complete"
        ));
    }

    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<List<PoiDto>>> getNearby(
            @RequestParam double lat,
            @RequestParam double lng) {
        return ResponseEntity.ok(ApiResponse.success(
                mapsClient.getNearbyPlaces(lat, lng),
                "Nearby POI points of interest"
        ));
    }
}
