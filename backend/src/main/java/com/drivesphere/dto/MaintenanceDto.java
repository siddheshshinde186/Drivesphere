package com.drivesphere.dto;

import com.drivesphere.entity.MaintenanceStatus;
import com.drivesphere.entity.ServiceType;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceDto {

    private Long id;
    private Long vehicleId;
    private String vehicleName;
    private String registrationNumber;
    private ServiceType serviceType;
    private LocalDate serviceDate;
    private LocalDate nextServiceDate;
    private String description;
    private BigDecimal cost;
    private MaintenanceStatus status;
    private BigDecimal odometerKm;
    private String notes;
    private Instant createdAt;
}
