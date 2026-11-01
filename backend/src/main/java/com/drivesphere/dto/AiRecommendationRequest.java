package com.drivesphere.dto;

import com.drivesphere.entity.FuelType;
import com.drivesphere.entity.Transmission;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiRecommendationRequest {

    private BigDecimal maxBudgetPerDay;
    private Integer passengers;
    private String tripType; // "CITY_COMMUTE", "FAMILY_VACATION", "BUSINESS_EXECUTIVE", "CARGO_DELIVERY", "LONG_ROADTRIP"
    private Integer luggageBags;
    private FuelType fuelPreference;
    private Transmission transmissionPreference;
    private BigDecimal estimatedDistanceKm;
    private List<String> preferredFeatures;
    private java.time.LocalDateTime startDateTime;
    private java.time.LocalDateTime endDateTime;
}
