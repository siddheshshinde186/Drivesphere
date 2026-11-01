package com.drivesphere.dto;

import com.drivesphere.entity.FuelType;
import com.drivesphere.entity.Transmission;
import com.drivesphere.entity.VehicleStatus;
import com.drivesphere.entity.VehicleType;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Restricted Vehicle DTO for public landing page, customer fleet exploration,
 * and trip planning. Strictly excludes sensitive internal fleet details:
 * - registrationNumber (license plate)
 * - vin (chassis number)
 * - exact GPS coordinates (latitude, longitude)
 * - internal odometer readings (currentOdometerKm)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicVehicleDto {

    private Long id;
    private String brand;
    private String model;
    private Integer year;
    private VehicleType vehicleType;
    private FuelType fuelType;
    private Transmission transmission;
    private Integer seatingCapacity;
    private BigDecimal mileage;
    private Integer rangeKm;
    private BigDecimal batteryCapacityKwh;
    private BigDecimal fuelTankCapacityL;
    private BigDecimal rentalPricePerDay;
    private BigDecimal rentalPricePerHour;
    private String description;
    private String location; // City/Hub name only (e.g. "Downtown Hub")
    private VehicleStatus status;
    private boolean available;
    private String primaryImageUrl;
    private List<String> imageUrls;
    private Long categoryId;
    private String categoryName;
    private BigDecimal averageRating;
    private Integer totalRatings;

    public String getDisplayName() {
        if (year != null && year > 0) {
            return brand + " " + model + " (" + year + ")";
        }
        return brand + " " + model;
    }
}

