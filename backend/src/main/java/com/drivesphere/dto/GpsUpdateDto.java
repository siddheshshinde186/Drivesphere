package com.drivesphere.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GpsUpdateDto {

    private Long tripId;
    private Long vehicleId;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal speedKmh;
    private BigDecimal headingDegrees;
    private BigDecimal batteryLevelPercent;
    private BigDecimal fuelLevelPercent;
}
