package com.drivesphere.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserStatsDto {
    private long totalBookings;
    private long activeRentals;
    private long completedTrips;
    private BigDecimal totalSpent;
}

