package com.drivesphere.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RentalActionRequest {

    private BigDecimal odometerKm;
    private String conditionNotes;
    private BigDecimal fuelLevelPercent;
    private BigDecimal extraCharges;
}
