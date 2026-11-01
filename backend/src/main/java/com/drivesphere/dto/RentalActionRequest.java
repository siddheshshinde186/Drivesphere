package com.drivesphere.dto;

import jakarta.validation.constraints.DecimalMin;
import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RentalActionRequest {

    @DecimalMin(value = "0.0", message = "Odometer reading cannot be negative")
    private BigDecimal odometerKm;
    private String conditionNotes;
    private BigDecimal fuelLevelPercent;
    @DecimalMin(value = "0.0", message = "Extra charges cannot be negative")
    private BigDecimal extraCharges;
}

