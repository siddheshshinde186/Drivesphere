package com.drivesphere.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleRecommendationDto {

    // Primary or single vehicle
    private VehicleDto vehicle;

    // Multi-vehicle & capacity attributes
    @Builder.Default
    private int vehicleCount = 1;
    private int totalSeatsCapacity;
    private String seatingCalculation;
    private BigDecimal perVehicleDailyRate;
    private BigDecimal totalDailyRental;
    private boolean multiVehicle;
    
    @Builder.Default
    private boolean available = true;
    private String availabilityNote;
    private List<FleetItemDto> fleetItems;

    // AI matching details
    private Integer matchPercentage;
    private String matchReason;
    private String bestUsedFor;
    private BigDecimal estimatedCost;
}
