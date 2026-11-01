package com.drivesphere.repository;

import com.drivesphere.entity.Driver;
import com.drivesphere.entity.User;
import com.drivesphere.entity.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {
    Optional<Driver> findByUser(User user);
    Optional<Driver> findByUserId(Long userId);
    Optional<Driver> findByLicenseNumber(String licenseNumber);

    List<Driver> findByAvailableTrue();

    @Query("SELECT d FROM Driver d WHERE d.available = true AND d.online = true AND d.lastSeenAt >= :threshold AND d.verification.status = 'APPROVED' AND d.user.active = true")
    List<Driver> findAvailableOnlineAndApprovedDrivers(@Param("threshold") LocalDateTime threshold);

    @Query("SELECT d FROM Driver d WHERE d.available = true AND d.verification.status = 'APPROVED'")
    List<Driver> findAvailableAndApprovedDrivers();

    @Query("SELECT d FROM Driver d WHERE d.verification.status = :status")
    List<Driver> findByVerificationStatus(@Param("status") VerificationStatus status);

    @Query("SELECT COUNT(d) FROM Driver d WHERE d.verification.status = :status")
    long countByVerificationStatus(@Param("status") VerificationStatus status);

    @Query("SELECT COUNT(d) FROM Driver d WHERE d.online = true AND d.lastSeenAt >= :threshold")
    long countOnlineDrivers(@Param("threshold") LocalDateTime threshold);

    @Query("SELECT COUNT(d) FROM Driver d WHERE d.available = false")
    long countBusyDrivers();

    @Query("SELECT COUNT(b) FROM Booking b WHERE b.driver.id = :driverId AND " +
            "(b.bookingStatus IN ('CONFIRMED', 'ACTIVE') OR " +
            "(b.bookingStatus IN ('HELD', 'PENDING', 'PENDING_PAYMENT') AND b.holdExpiresAt > CURRENT_TIMESTAMP)) AND " +
            "((b.startDateTime <= :endDateTime AND b.endDateTime >= :startDateTime))")
    long countOverlappingDriverBookings(@Param("driverId") Long driverId,
                                        @Param("startDateTime") LocalDateTime startDateTime,
                                        @Param("endDateTime") LocalDateTime endDateTime);
}
