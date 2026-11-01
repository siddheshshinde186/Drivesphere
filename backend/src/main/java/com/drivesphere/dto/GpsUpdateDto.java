package com.drivesphere.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GpsUpdateDto {

    @NotNull(message = "Trip ID is required")
    private Long tripId;

    private Long vehicleId;

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0", message = "Latitude must be >= -90")
    @DecimalMax(value = "90.0", message = "Latitude must be <= 90")
    private BigDecimal latitude;

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0", message = "Longitude must be >= -180")
    @DecimalMax(value = "180.0", message = "Longitude must be <= 180")
    private BigDecimal longitude;

    @DecimalMin(value = "0.0", message = "Speed cannot be negative")
    private BigDecimal speedKmh;

    private BigDecimal headingDegrees;
    private BigDecimal batteryLevelPercent;
    private BigDecimal fuelLevelPercent;
}

