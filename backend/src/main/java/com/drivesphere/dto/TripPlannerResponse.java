package com.drivesphere.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripPlannerResponse {

    private String source;
    private String destination;
    private String destinationLocationName; // Resolved name e.g. "Mysore, Karnataka, India"
    private BigDecimal distanceKm;
    private Integer durationMinutes;
    private String durationFormatted;

    private Long vehicleId;
    private String vehicleName;
    private BigDecimal vehicleMileage;
    private BigDecimal fuelUsedUnits;
    private BigDecimal fuelPricePerUnit;
    private BigDecimal estimatedFuelCost;
    
    @Builder.Default
    private String energyUnit = "L"; // "kWh" for EV, "L" for ICE/Hybrid
    private boolean isElectric;

    private List<List<Double>> routeCoordinates; // [[lat, lng], [lat, lng], ...]
    private WeatherSummaryDto destinationWeather;
    private List<PoiDto> nearbyPlaces;
    private List<String> travelTips;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WeatherSummaryDto {
        private String locationName;
        private Double temperature;
        private Double windSpeed;
        private Integer weatherCode;
        private String condition;
        private Integer precipitationProbability;
        private String forecastNotice;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PoiDto {
        private String name;
        private String category; // "PETROL_PUMP", "EV_CHARGER", "HOTEL", "RESTAURANT", "HOSPITAL"
        private Double latitude;
        private Double longitude;
        private String address;
        private Double distanceKm;
    }
}
