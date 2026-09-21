package com.drivesphere.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripPlannerRequest {

    @NotBlank(message = "Source location is required")
    private String source;

    @NotBlank(message = "Destination location is required")
    private String destination;

    private BigDecimal sourceLat;
    private BigDecimal sourceLng;
    private BigDecimal destLat;
    private BigDecimal destLng;

    private Long vehicleId;

    @Builder.Default
    private BigDecimal fuelPricePerUnit = BigDecimal.valueOf(102.50); // Fuel price per liter (or kWh)
}
