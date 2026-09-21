package com.drivesphere.dto;

import lombok.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FleetItemDto {
    private VehicleDto vehicle;
    private int quantity;
    private int seatsPerVehicle;
    private int totalSeats;
    private BigDecimal dailyRatePerVehicle;
    private BigDecimal subtotalDailyRate;
}
