package com.drivesphere.dto;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiRecommendationResponse {

    private int requestedPassengers;
    private boolean multiVehicleRequired;
    private int totalAvailableFleetCapacity;
    
    @Builder.Default
    private String status = "SUCCESS"; // "SUCCESS" or "NO_FLEET_AVAILABLE"
    
    private List<VehicleRecommendationDto> recommendations;
    private String summaryExplanation;
    private boolean generatedByGemini;
}
