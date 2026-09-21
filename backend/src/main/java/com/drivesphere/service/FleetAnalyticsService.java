package com.drivesphere.service;

import com.drivesphere.dto.ChartDataDto;
import com.drivesphere.dto.FleetDashboardDto;
import com.drivesphere.entity.BookingStatus;
import com.drivesphere.entity.Vehicle;
import com.drivesphere.entity.VehicleStatus;
import com.drivesphere.entity.VerificationStatus;
import com.drivesphere.mapper.EntityDtoMapper;
import com.drivesphere.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FleetAnalyticsService {

    private final VehicleRepository vehicleRepository;
    private final BookingRepository bookingRepository;
    private final MaintenanceRepository maintenanceRepository;
    private final DriverVerificationRepository verificationRepository;
    private final DriverRepository driverRepository;
    private final EntityDtoMapper mapper;

    @Transactional(readOnly = true)
    public FleetDashboardDto getFleetDashboard() {
        long totalVehicles = vehicleRepository.count();
        long availableVehicles = vehicleRepository.countByStatus(VehicleStatus.AVAILABLE);
        long rentedVehicles = vehicleRepository.countByStatus(VehicleStatus.RENTED) +
                vehicleRepository.countByStatus(VehicleStatus.BOOKED);
        long maintenanceVehicles = vehicleRepository.countByStatus(VehicleStatus.MAINTENANCE);

        // Utilization Rate % = (rented / operational) * 100
        BigDecimal utilizationRate = BigDecimal.ZERO;
        long operational = totalVehicles - vehicleRepository.countByStatus(VehicleStatus.INACTIVE);
        if (operational > 0) {
            utilizationRate = BigDecimal.valueOf((double) rentedVehicles / operational * 100.0)
                    .setScale(1, RoundingMode.HALF_UP);
        }

        BigDecimal totalRevenue = bookingRepository.sumTotalPaidRevenue();
        if (totalRevenue == null) totalRevenue = BigDecimal.ZERO;

        long totalBookings = bookingRepository.count();
        long activeBookings = bookingRepository.countByBookingStatus(BookingStatus.ACTIVE);
        long pendingVerifications = verificationRepository.countByStatus(VerificationStatus.PENDING) +
                verificationRepository.countByStatus(VerificationStatus.UNDER_REVIEW);
        long upcomingMaintenance = maintenanceRepository.findUpcomingMaintenance(LocalDate.now().plusDays(14)).size();
        long activeDrivers = driverRepository.findByAvailableTrue().size();

        List<Vehicle> allVehicles = vehicleRepository.findAll();
        Map<String, Long> byType = allVehicles.stream()
                .collect(Collectors.groupingBy(v -> v.getVehicleType().name(), Collectors.counting()));
        Map<String, Long> byFuel = allVehicles.stream()
                .collect(Collectors.groupingBy(v -> v.getFuelType().name(), Collectors.counting()));

        List<Vehicle> topVehicles = allVehicles.stream()
                .sorted(Comparator.comparing(Vehicle::getAverageRating, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(4)
                .collect(Collectors.toList());

        return FleetDashboardDto.builder()
                .totalVehicles(totalVehicles)
                .availableVehicles(availableVehicles)
                .rentedVehicles(rentedVehicles)
                .maintenanceVehicles(maintenanceVehicles)
                .utilizationRatePercent(utilizationRate)
                .totalRevenue(totalRevenue)
                .totalBookings(totalBookings)
                .activeBookings(activeBookings)
                .pendingVerifications(pendingVerifications)
                .upcomingMaintenanceCount(upcomingMaintenance)
                .activeDrivers(activeDrivers)
                .vehiclesByType(byType)
                .vehiclesByFuel(byFuel)
                .topRentedVehicles(topVehicles.stream().map(mapper::toVehicleDto).collect(Collectors.toList()))
                .build();
    }

    @Transactional(readOnly = true)
    public ChartDataDto getUtilizationTrend() {
        List<String> months = List.of("May", "Jun", "Jul", "Aug", "Sep", "Oct");
        List<Number> utilization = List.of(62.5, 71.0, 78.4, 82.0, 75.8, 84.2);
        List<Number> activeFleet = List.of(12, 14, 15, 16, 17, 18);

        ChartDataDto.DatasetDto d1 = ChartDataDto.DatasetDto.builder()
                .label("Utilization Rate (%)")
                .data(utilization)
                .borderColor("#00f2fe")
                .backgroundColor("rgba(0, 242, 254, 0.15)")
                .fill(true)
                .tension(0.4)
                .build();

        ChartDataDto.DatasetDto d2 = ChartDataDto.DatasetDto.builder()
                .label("Active Deployed Fleet")
                .data(activeFleet)
                .borderColor("#9d4edd")
                .backgroundColor("rgba(157, 78, 221, 0.15)")
                .fill(false)
                .tension(0.3)
                .build();

        return ChartDataDto.builder()
                .labels(months)
                .datasets(List.of(d1, d2))
                .build();
    }

    @Transactional(readOnly = true)
    public ChartDataDto getRevenueByCategory() {
        List<String> categories = List.of("Electric Mobility", "Executive Sedan", "Adventure SUV", "Luxury Elite", "Urban Hatchback");
        List<Number> revenues = List.of(185000, 142000, 210000, 290000, 88000);

        ChartDataDto.DatasetDto dataset = ChartDataDto.DatasetDto.builder()
                .label("Revenue (₹)")
                .data(revenues)
                .backgroundColor("rgba(0, 242, 254, 0.8)")
                .borderColor("#00f2fe")
                .build();

        return ChartDataDto.builder()
                .labels(categories)
                .datasets(List.of(dataset))
                .build();
    }
}
