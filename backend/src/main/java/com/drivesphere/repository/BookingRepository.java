package com.drivesphere.repository;

import com.drivesphere.entity.Booking;
import com.drivesphere.entity.BookingStatus;
import com.drivesphere.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByBookingReference(String bookingReference);
    List<Booking> findByCustomerOrderByCreatedAtDesc(User customer);
    List<Booking> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    List<Booking> findByDriverIdOrderByCreatedAtDesc(Long driverId);
    List<Booking> findByVehicleIdOrderByCreatedAtDesc(Long vehicleId);
    List<Booking> findByVehicleIdAndBookingStatusIn(Long vehicleId, java.util.Collection<BookingStatus> statuses);
    List<Booking> findByBookingStatusOrderByCreatedAtDesc(BookingStatus bookingStatus);
    long countByBookingStatus(BookingStatus bookingStatus);

    List<Booking> findByBookingStatusInAndHoldExpiresAtBefore(java.util.Collection<BookingStatus> statuses, java.time.LocalDateTime dateTime);

    @Query("SELECT COALESCE(SUM(b.totalAmount), 0) FROM Booking b WHERE b.paymentStatus = 'PAID'")
    BigDecimal sumTotalPaidRevenue();

    @Query("SELECT COALESCE(SUM(b.totalAmount), 0) FROM Booking b WHERE b.vehicle.id = :vehicleId AND b.paymentStatus = 'PAID'")
    BigDecimal sumRevenueByVehicle(Long vehicleId);

    @Query("SELECT c.name, COALESCE(SUM(b.totalAmount), 0) FROM Booking b JOIN b.vehicle v JOIN v.category c WHERE b.paymentStatus = 'PAID' GROUP BY c.name")
    List<Object[]> findRevenueByCategory();

    @Query("SELECT FUNCTION('MONTH', b.startDateTime), FUNCTION('YEAR', b.startDateTime), COALESCE(SUM(b.totalAmount), 0), COUNT(b) FROM Booking b WHERE b.paymentStatus = 'PAID' GROUP BY FUNCTION('YEAR', b.startDateTime), FUNCTION('MONTH', b.startDateTime) ORDER BY FUNCTION('YEAR', b.startDateTime) ASC, FUNCTION('MONTH', b.startDateTime) ASC")
    List<Object[]> findMonthlyRevenueTrend();
}
