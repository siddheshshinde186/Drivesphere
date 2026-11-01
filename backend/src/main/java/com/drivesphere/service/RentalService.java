package com.drivesphere.service;

import com.drivesphere.dto.BookingDto;
import com.drivesphere.dto.RentalActionRequest;
import com.drivesphere.entity.*;
import com.drivesphere.exception.ResourceNotFoundException;
import com.drivesphere.mapper.EntityDtoMapper;
import com.drivesphere.repository.BookingRepository;
import com.drivesphere.repository.DriverRepository;
import com.drivesphere.repository.TripRepository;
import com.drivesphere.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RentalService {

    private final BookingRepository bookingRepository;
    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final TripRepository tripRepository;
    private final NotificationService notificationService;
    private final EntityDtoMapper mapper;

    @Autowired(required = false)
    private VehicleService vehicleService;

    public void setVehicleService(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @Transactional
    public BookingDto pickupVehicle(Long bookingId, RentalActionRequest request) {
        return pickupVehicle(bookingId, request, null, true);
    }

    @Transactional
    public BookingDto pickupVehicle(Long bookingId, RentalActionRequest request, Long currentUserId, boolean isStaff) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        if (!isStaff) {
            boolean isAssignedDriver = booking.getDriver() != null && booking.getDriver().getUser() != null
                    && booking.getDriver().getUser().getId().equals(currentUserId);
            boolean isBookingCustomer = booking.getCustomer() != null && booking.getCustomer().getId().equals(currentUserId);
            if (!isAssignedDriver && !isBookingCustomer) {
                throw new AccessDeniedException("Unauthorized: You do not own this booking.");
            }
        }

        if (booking.getBookingStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Booking must be CONFIRMED before pickup. Current status: " + booking.getBookingStatus());
        }

        if (booking.getPaymentStatus() != PaymentStatus.PAID) {
            throw new IllegalStateException("Payment must be completed before pickup. Current payment status: " + booking.getPaymentStatus());
        }

        LocalDateTime now = LocalDateTime.now();
        // Pickup Eligibility Rule: Trip can only be started when the permitted pickup window has arrived (within 2 hours before start time)
        if (booking.getStartDateTime() != null && now.isBefore(booking.getStartDateTime().minusHours(2))) {
            throw new IllegalStateException("Booking pickup is scheduled for " + booking.getStartDateTime().toString().replace('T', ' ')
                    + ". Trip cannot be started until permitted pickup window.");
        }

        if (booking.getEndDateTime() != null && now.isAfter(booking.getEndDateTime())) {
            throw new IllegalStateException("Booking rental schedule expired on " + booking.getEndDateTime().toString().replace('T', ' ') + ".");
        }

        booking.setBookingStatus(BookingStatus.ACTIVE);
        booking.setPickupTime(now);

        // Update Vehicle status to RENTED
        Vehicle vehicle = booking.getVehicle();
        vehicle.setStatus(VehicleStatus.RENTED);
        vehicleRepository.save(vehicle);

        // Start Trip
        tripRepository.findByBookingId(bookingId).ifPresent(trip -> {
            trip.setStatus(TripStatus.IN_PROGRESS);
            trip.setStartTime(LocalDateTime.now());
            tripRepository.save(trip);
        });

        notificationService.createNotification(
                booking.getCustomer(),
                "Vehicle Handover Complete",
                "Your rental for " + vehicle.getBrand() + " " + vehicle.getModel() + " is now ACTIVE. Enjoy your safe journey!",
                NotificationType.TRIP,
                "BOOKING",
                booking.getId()
        );

        return mapper.toBookingDto(bookingRepository.save(booking));
    }

    @Transactional
    public BookingDto returnVehicle(Long bookingId, RentalActionRequest request) {
        return returnVehicle(bookingId, request, null, true);
    }

    @Transactional
    public BookingDto returnVehicle(Long bookingId, RentalActionRequest request, Long currentUserId, boolean isStaff) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        if (!isStaff) {
            boolean isAssignedDriver = booking.getDriver() != null && booking.getDriver().getUser() != null
                    && booking.getDriver().getUser().getId().equals(currentUserId);
            boolean isBookingCustomer = booking.getCustomer() != null && booking.getCustomer().getId().equals(currentUserId);
            if (!isAssignedDriver && !isBookingCustomer) {
                throw new AccessDeniedException("Unauthorized: You do not own this booking.");
            }
        }

        if (booking.getBookingStatus() != BookingStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE rentals can be returned. Current status: " + booking.getBookingStatus());
        }

        if (request != null && request.getExtraCharges() != null) {
            if (request.getExtraCharges().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Extra charges cannot be negative.");
            }
            if (request.getExtraCharges().signum() > 0) {
                booking.setTotalAmount(booking.getTotalAmount().add(request.getExtraCharges()));
            }
        }

        booking.setBookingStatus(BookingStatus.COMPLETED);
        booking.setReturnTime(LocalDateTime.now());

        // Return Vehicle through centralized operational reconciliation
        Vehicle vehicle = booking.getVehicle();
        if (request != null && request.getOdometerKm() != null) {
            vehicle.setCurrentOdometerKm(request.getOdometerKm());
        }
        if (vehicleService != null) {
            vehicleService.reconcileVehicleOperationalStatus(vehicle);
        } else {
            vehicle.setStatus(VehicleStatus.AVAILABLE);
            vehicle.setAvailable(true);
            vehicleRepository.save(vehicle);
        }

        // Release Driver: Only mark available if driver is actively online
        if (booking.getDriver() != null) {
            Driver driver = booking.getDriver();
            driver.setAvailable(driver.isOnline());
            int currentTrips = driver.getTotalTrips() != null ? driver.getTotalTrips() : 0;
            driver.setTotalTrips(currentTrips + 1);
            driverRepository.save(driver);
        }

        // Complete Trip
        tripRepository.findByBookingId(bookingId).ifPresent(trip -> {
            trip.setStatus(TripStatus.COMPLETED);
            trip.setEndTime(LocalDateTime.now());
            trip.setDistanceTraveledKm(trip.getDistanceKm());
            tripRepository.save(trip);
        });

        notificationService.createNotification(
                booking.getCustomer(),
                "Trip Completed – Thank You!",
                "Your rental has been successfully completed and returned. Please leave a rating and review your experience!",
                NotificationType.TRIP,
                "BOOKING",
                booking.getId()
        );

        return mapper.toBookingDto(bookingRepository.save(booking));
    }
}
