package com.drivesphere.dto;

import com.drivesphere.entity.ServiceType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceCreateRequest {

    @NotNull(message = "Vehicle ID is required")
    private Long vehicleId;

    @NotNull(message = "Service type is required")
    private ServiceType serviceType;

    @NotNull(message = "Service date is required")
    private LocalDate serviceDate;

    private LocalDate nextServiceDate;

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Cost is required")
    @DecimalMin(value = "0.0", message = "Cost must be non-negative")
    private BigDecimal cost;

    private BigDecimal odometerKm;
    private String notes;
}
