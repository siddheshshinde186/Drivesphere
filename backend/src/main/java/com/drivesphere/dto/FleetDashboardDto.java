package com.drivesphere.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FleetDashboardDto {

    private long totalVehicles;
    private long availableVehicles;
    private long rentedVehicles;
    private long maintenanceVehicles;
    private BigDecimal utilizationRatePercent;
    private BigDecimal totalRevenue;
    private long totalBookings;
    private long activeBookings;
    private long pendingVerifications;
    private long upcomingMaintenanceCount;
    private long activeDrivers;
    private Map<String, Long> vehiclesByType;
    private Map<String, Long> vehiclesByFuel;
    private List<VehicleDto> topRentedVehicles;
}
