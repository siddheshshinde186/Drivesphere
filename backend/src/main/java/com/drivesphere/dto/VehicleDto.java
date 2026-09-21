package com.drivesphere.dto;

import com.drivesphere.entity.FuelType;
import com.drivesphere.entity.Transmission;
import com.drivesphere.entity.VehicleStatus;
import com.drivesphere.entity.VehicleType;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleDto {

    private Long id;
    private String registrationNumber;
    private String vin;
    private String brand;
    private String model;
    private Integer year;
    private VehicleType vehicleType;
    private FuelType fuelType;
    private Transmission transmission;
    private Integer seatingCapacity;
    private BigDecimal mileage;
    private BigDecimal batteryCapacityKwh;
    private BigDecimal fuelTankCapacityL;
    private BigDecimal rentalPricePerDay;
    private BigDecimal rentalPricePerHour;
    private String description;
    private String location;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private VehicleStatus status;
    private boolean available;
    private BigDecimal currentOdometerKm;
    private String primaryImageUrl;
    private List<String> imageUrls;
    private Long categoryId;
    private String categoryName;
    private BigDecimal averageRating;
    private Integer totalRatings;
    private Instant createdAt;

    public String getDisplayName() {
        return brand + " " + model + " (" + year + ")";
    }
}
