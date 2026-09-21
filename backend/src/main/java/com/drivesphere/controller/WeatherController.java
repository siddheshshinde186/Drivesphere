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
        return ResponseEntity.ok(ApiResponse.success(
                weatherClient.getWeather(lat, lng),
                "Destination weather conditions retrieved via Open-Meteo"
        ));
    }
}
