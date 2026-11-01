package com.drivesphere.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "vehicles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vehicle extends BaseEntity {

    @Column(name = "registration_number", length = 30, nullable = false, unique = true)
    private String registrationNumber;

    @Column(name = "vin", length = 50, unique = true)
    private String vin;

    @Column(name = "brand", length = 50, nullable = false)
    private String brand;

    @Column(name = "model", length = 50, nullable = false)
    private String model;

    @Column(name = "manufacture_year", nullable = false)
    private Integer year;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", length = 30, nullable = false)
    private VehicleType vehicleType;

    @Enumerated(EnumType.STRING)
    @Column(name = "fuel_type", length = 30, nullable = false)
    private FuelType fuelType;

    @Enumerated(EnumType.STRING)
    @Column(name = "transmission", length = 30, nullable = false)
    private Transmission transmission;

    @Column(name = "seating_capacity", nullable = false)
    private Integer seatingCapacity;

    @Column(name = "mileage", precision = 6, scale = 2, nullable = false)
    private BigDecimal mileage; // km per liter for ICE/Hybrid

    @Column(name = "range_km")
    private Integer rangeKm; // Rated full battery/tank range in km

    @Column(name = "battery_capacity_kwh", precision = 6, scale = 2)
    private BigDecimal batteryCapacityKwh;

    @Column(name = "energy_consumption_kwh_per_100km", precision = 5, scale = 2)
    private BigDecimal energyConsumptionKwhPer100Km;

    @Column(name = "fuel_tank_capacity_l", precision = 6, scale = 2)
    private BigDecimal fuelTankCapacityL;

    @Column(name = "rental_price_per_day", precision = 10, scale = 2, nullable = false)
    private BigDecimal rentalPricePerDay;

    @Column(name = "rental_price_per_hour", precision = 10, scale = 2, nullable = false)
    private BigDecimal rentalPricePerHour;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "location", length = 150)
    private String location;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", length = 30, nullable = false)
    private VehicleStatus status = VehicleStatus.AVAILABLE;

    @Builder.Default
    @Column(name = "is_available", nullable = false)
    private boolean available = true;

    @Column(name = "current_odometer_km", precision = 10, scale = 2)
    private BigDecimal currentOdometerKm;

    @Column(name = "last_service_odometer_km", precision = 10, scale = 2)
    private BigDecimal lastServiceOdometerKm;

    @Column(name = "last_service_date")
    private java.time.LocalDate lastServiceDate;

    @Column(name = "primary_image_url", length = 500)
    private String primaryImageUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private VehicleCategory category;

    @Builder.Default
    @OneToMany(mappedBy = "vehicle", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VehicleImage> images = new ArrayList<>();

    @Builder.Default
    @Column(name = "average_rating", precision = 3, scale = 2)
    private BigDecimal averageRating = BigDecimal.valueOf(5.00);

    @Builder.Default
    @Column(name = "total_ratings")
    private Integer totalRatings = 0;
}
