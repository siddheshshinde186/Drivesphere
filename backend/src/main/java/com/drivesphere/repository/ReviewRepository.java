package com.drivesphere.repository;

import com.drivesphere.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByVehicleIdOrderByCreatedAtDesc(Long vehicleId);
    List<Review> findByDriverIdOrderByCreatedAtDesc(Long driverId);
    Optional<Review> findByBookingId(Long bookingId);

    List<Review> findAllByOrderByCreatedAtDesc();

    @Query("SELECT AVG(r.rating) FROM Review r")
    BigDecimal findOverallAverageRating();

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.vehicle.id = :vehicleId")
    BigDecimal findAverageRatingByVehicle(Long vehicleId);

    @Query("SELECT AVG(r.driverRating) FROM Review r WHERE r.driver.id = :driverId AND r.driverRating IS NOT NULL")
    BigDecimal findAverageRatingByDriver(Long driverId);
}
