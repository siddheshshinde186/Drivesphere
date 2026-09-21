package com.drivesphere.dto;

import com.drivesphere.entity.FuelType;
import com.drivesphere.entity.Transmission;
import com.drivesphere.entity.VehicleStatus;
import com.drivesphere.entity.VehicleType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleCreateUpdateRequest {

    @NotBlank(message = "Registration number is required")
    private String registrationNumber;

    private String vin;

    @NotBlank(message = "Brand is required")
    private String brand;

    @NotBlank(message = "Model is required")
    private String model;

    @NotNull(message = "Year is required")
    @Min(value = 2000, message = "Year must be 2000 or later")
    private Integer year;

    @NotNull(message = "Vehicle type is required")
    private VehicleType vehicleType;

    @NotNull(message = "Fuel type is required")
    private FuelType fuelType;

    @NotNull(message = "Transmission is required")
    private Transmission transmission;

    @NotNull(message = "Seating capacity is required")
    @Min(value = 1, message = "Seating capacity must be at least 1")
    private Integer seatingCapacity;

    @NotNull(message = "Mileage is required")
    @DecimalMin(value = "1.0", message = "Mileage must be positive")
    private BigDecimal mileage;

    private BigDecimal batteryCapacityKwh;
    private BigDecimal fuelTankCapacityL;

    @NotNull(message = "Daily rental price is required")
    @DecimalMin(value = "0.0", message = "Price must be non-negative")
    private BigDecimal rentalPricePerDay;

    @NotNull(message = "Hourly rental price is required")
    @DecimalMin(value = "0.0", message = "Price must be non-negative")
    private BigDecimal rentalPricePerHour;

    private String description;
    private String location;
    private BigDecimal latitude;
    private BigDecimal longitude;

    private VehicleStatus status;
    private Boolean available;
    private BigDecimal currentOdometerKm;
    private String primaryImageUrl;
    private List<String> imageUrls;
    private Long categoryId;
}
