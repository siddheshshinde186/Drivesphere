package com.drivesphere.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingCreateRequest {

    @NotNull(message = "Vehicle ID is required")
    private Long vehicleId;

    private Long driverId; // Optional: customer can hire a driver with vehicle

    @NotNull(message = "Start date/time is required")
    @FutureOrPresent(message = "Start time must be present or future")
    private LocalDateTime startDateTime;

    @NotNull(message = "End date/time is required")
    private LocalDateTime endDateTime;

    @NotBlank(message = "Pickup location is required")
    private String pickupLocation;

    @NotBlank(message = "Drop location is required")
    private String dropLocation;

    private BigDecimal pickupLatitude;
    private BigDecimal pickupLongitude;
    private BigDecimal dropLatitude;
    private BigDecimal dropLongitude;

    private String notes;
}
