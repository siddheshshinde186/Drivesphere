package com.drivesphere.repository;

import com.drivesphere.entity.FuelType;
import com.drivesphere.entity.Transmission;
import com.drivesphere.entity.Vehicle;
import com.drivesphere.entity.VehicleStatus;
import com.drivesphere.entity.VehicleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    Optional<Vehicle> findByRegistrationNumber(String registrationNumber);
    List<Vehicle> findByStatus(VehicleStatus status);
    List<Vehicle> findByAvailableTrue();
    long countByStatus(VehicleStatus status);

    @Query("SELECT v FROM Vehicle v WHERE v.available = true AND v.status = 'AVAILABLE'")
    List<Vehicle> findOperationalAvailableVehicles();

    @Query("SELECT COUNT(b) FROM Booking b WHERE b.vehicle.id = :vehicleId AND b.bookingStatus IN ('CONFIRMED', 'ACTIVE') AND ((b.startDateTime <= :endDateTime AND b.endDateTime >= :startDateTime))")
    long countOverlappingVehicleBookings(@Param("vehicleId") Long vehicleId,
                                         @Param("startDateTime") LocalDateTime startDateTime,
                                         @Param("endDateTime") LocalDateTime endDateTime);

    @Query("SELECT v FROM Vehicle v WHERE v.available = true AND v.status = 'AVAILABLE' AND v.id NOT IN (" +
            "SELECT b.vehicle.id FROM Booking b WHERE b.bookingStatus IN ('CONFIRMED', 'ACTIVE') AND " +
            "((b.startDateTime <= :endDateTime AND b.endDateTime >= :startDateTime)))")
    List<Vehicle> findAvailableVehiclesForPeriod(@Param("startDateTime") LocalDateTime startDateTime,
                                                @Param("endDateTime") LocalDateTime endDateTime);

    @Query("SELECT v FROM Vehicle v WHERE (:vehicleType IS NULL OR v.vehicleType = :vehicleType) " +
            "AND (:fuelType IS NULL OR v.fuelType = :fuelType) " +
            "AND (:transmission IS NULL OR v.transmission = :transmission) " +
            "AND (:minSeats IS NULL OR v.seatingCapacity >= :minSeats) " +
            "AND (:maxPrice IS NULL OR v.rentalPricePerDay <= :maxPrice) " +
            "AND (:brand IS NULL OR LOWER(v.brand) LIKE LOWER(CONCAT('%', :brand, '%')))")
    List<Vehicle> filterVehicles(@Param("vehicleType") VehicleType vehicleType,
                                 @Param("fuelType") FuelType fuelType,
                                 @Param("transmission") Transmission transmission,
                                 @Param("minSeats") Integer minSeats,
                                 @Param("maxPrice") BigDecimal maxPrice,
                                 @Param("brand") String brand);
}
