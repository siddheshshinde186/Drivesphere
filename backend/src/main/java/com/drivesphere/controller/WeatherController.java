package com.drivesphere.controller;

import com.drivesphere.dto.ApiResponse;
import com.drivesphere.dto.TripPlannerResponse.WeatherSummaryDto;
import com.drivesphere.integration.weather.WeatherClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherClient weatherClient;

    @GetMapping
    public ResponseEntity<ApiResponse<WeatherSummaryDto>> getWeather(
            @RequestParam double lat,
            @RequestParam double lng) {
        if (lat < -90.0 || lat > 90.0) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90 degrees.");
        }
        if (lng < -180.0 || lng > 180.0) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180 degrees.");
        }
        return ResponseEntity.ok(ApiResponse.success(
                weatherClient.getWeather(lat, lng),
                "Destination weather conditions retrieved via Open-Meteo"
        ));
    }
}
