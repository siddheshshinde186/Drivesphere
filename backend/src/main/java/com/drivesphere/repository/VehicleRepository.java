package com.drivesphere.repository;

import com.drivesphere.entity.FuelType;
import com.drivesphere.entity.Transmission;
import com.drivesphere.entity.Vehicle;
import com.drivesphere.entity.VehicleStatus;
import com.drivesphere.entity.VehicleType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Vehicle v WHERE v.id = :id")
    Optional<Vehicle> findByIdWithLock(@Param("id") Long id);

    @Query("SELECT v FROM Vehicle v WHERE v.available = true AND v.status = com.drivesphere.entity.VehicleStatus.AVAILABLE")
    List<Vehicle> findOperationalAvailableVehicles();

    @Query("SELECT v FROM Vehicle v WHERE v.status != com.drivesphere.entity.VehicleStatus.INACTIVE")
    List<Vehicle> findCustomerVisibleFleet();

    @Query("SELECT COUNT(b) FROM Booking b WHERE b.vehicle.id = :vehicleId AND " +
            "(b.bookingStatus IN (com.drivesphere.entity.BookingStatus.CONFIRMED, com.drivesphere.entity.BookingStatus.ACTIVE) OR " +
            "(b.bookingStatus IN (com.drivesphere.entity.BookingStatus.HELD, com.drivesphere.entity.BookingStatus.PENDING, com.drivesphere.entity.BookingStatus.PENDING_PAYMENT) AND b.holdExpiresAt > CURRENT_TIMESTAMP)) AND " +
            "(b.startDateTime < :endDateTime AND b.endDateTime > :startDateTime)")
    long countOverlappingVehicleBookings(@Param("vehicleId") Long vehicleId,
                                         @Param("startDateTime") LocalDateTime startDateTime,
                                         @Param("endDateTime") LocalDateTime endDateTime);

    @Query("SELECT v FROM Vehicle v WHERE v.status NOT IN (com.drivesphere.entity.VehicleStatus.INACTIVE, com.drivesphere.entity.VehicleStatus.MAINTENANCE) AND v.id NOT IN (" +
            "SELECT b.vehicle.id FROM Booking b WHERE " +
            "(b.bookingStatus IN (com.drivesphere.entity.BookingStatus.CONFIRMED, com.drivesphere.entity.BookingStatus.ACTIVE) OR " +
            "(b.bookingStatus IN (com.drivesphere.entity.BookingStatus.HELD, com.drivesphere.entity.BookingStatus.PENDING, com.drivesphere.entity.BookingStatus.PENDING_PAYMENT) AND b.holdExpiresAt > CURRENT_TIMESTAMP)) AND " +
            "(b.startDateTime < :endDateTime AND b.endDateTime > :startDateTime))")
    List<Vehicle> findAvailableVehiclesForPeriod(@Param("startDateTime") LocalDateTime startDateTime,
                                                @Param("endDateTime") LocalDateTime endDateTime);

    @Query("SELECT v FROM Vehicle v WHERE " +
            "v.status NOT IN (com.drivesphere.entity.VehicleStatus.INACTIVE, com.drivesphere.entity.VehicleStatus.MAINTENANCE) " +
            "AND (:vehicleType IS NULL OR v.vehicleType = :vehicleType) " +
            "AND (:fuelType IS NULL OR v.fuelType = :fuelType) " +
            "AND (:transmission IS NULL OR v.transmission = :transmission) " +
            "AND (:minSeats IS NULL OR v.seatingCapacity >= :minSeats) " +
            "AND (:maxPrice IS NULL OR v.rentalPricePerDay <= :maxPrice) " +
            "AND (:brand IS NULL OR LOWER(v.brand) LIKE LOWER(CONCAT('%', :brand, '%')))")
    List<Vehicle> filterVehiclesBase(@Param("vehicleType") VehicleType vehicleType,
                                     @Param("fuelType") FuelType fuelType,
                                     @Param("transmission") Transmission transmission,
                                     @Param("minSeats") Integer minSeats,
                                     @Param("maxPrice") BigDecimal maxPrice,
                                     @Param("brand") String brand);

    @Query("SELECT DISTINCT b.vehicle.id FROM Booking b WHERE " +
            "(b.bookingStatus IN (com.drivesphere.entity.BookingStatus.CONFIRMED, com.drivesphere.entity.BookingStatus.ACTIVE) OR " +
            "(b.bookingStatus IN (com.drivesphere.entity.BookingStatus.HELD, com.drivesphere.entity.BookingStatus.PENDING, com.drivesphere.entity.BookingStatus.PENDING_PAYMENT) AND b.holdExpiresAt > CURRENT_TIMESTAMP)) AND " +
            "(b.startDateTime < :endDateTime AND b.endDateTime > :startDateTime)")
    List<Long> findBookedVehicleIdsForPeriod(@Param("startDateTime") LocalDateTime startDateTime,
                                             @Param("endDateTime") LocalDateTime endDateTime);

    default List<Vehicle> filterVehicles(VehicleType vehicleType,
                                         FuelType fuelType,
                                         Transmission transmission,
                                         Integer minSeats,
                                         BigDecimal maxPrice,
                                         String brand,
                                         LocalDateTime startDateTime,
                                         LocalDateTime endDateTime) {
        List<Vehicle> vehicles = filterVehiclesBase(vehicleType, fuelType, transmission, minSeats, maxPrice, brand);
        if (startDateTime != null && endDateTime != null) {
            List<Long> bookedIds = findBookedVehicleIdsForPeriod(startDateTime, endDateTime);
            if (!bookedIds.isEmpty()) {
                return vehicles.stream()
                        .filter(v -> !bookedIds.contains(v.getId()))
                        .toList();
            }
        }
        return vehicles;
    }

    default List<Vehicle> filterVehicles(VehicleType vehicleType,
                                         FuelType fuelType,
                                         Transmission transmission,
                                         Integer minSeats,
                                         BigDecimal maxPrice,
                                         String brand) {
        return filterVehicles(vehicleType, fuelType, transmission, minSeats, maxPrice, brand, null, null);
    }
}
