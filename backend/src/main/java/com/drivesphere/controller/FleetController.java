package com.drivesphere.controller;

import com.drivesphere.dto.ApiResponse;
import com.drivesphere.dto.ChartDataDto;
import com.drivesphere.dto.FleetDashboardDto;
import com.drivesphere.service.FleetAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fleet")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('FLEET_MANAGER', 'ADMIN')")
public class FleetController {

    private final FleetAnalyticsService fleetAnalyticsService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<FleetDashboardDto>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.success(
                fleetAnalyticsService.getFleetDashboard(),
                "Fleet operational intelligence metrics"
        ));
    }

    @GetMapping("/utilization-trend")
    public ResponseEntity<ApiResponse<ChartDataDto>> getUtilizationTrend() {
        return ResponseEntity.ok(ApiResponse.success(
                fleetAnalyticsService.getUtilizationTrend(),
                "Fleet utilization trend chart data"
        ));
    }

    @GetMapping("/revenue-category")
    public ResponseEntity<ApiResponse<ChartDataDto>> getRevenueByCategory() {
        return ResponseEntity.ok(ApiResponse.success(
                fleetAnalyticsService.getRevenueByCategory(),
                "Revenue by vehicle category chart data"
        ));
    }
}
