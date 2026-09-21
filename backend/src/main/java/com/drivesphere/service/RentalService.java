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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public BookingDto pickupVehicle(Long bookingId, RentalActionRequest request) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        if (booking.getBookingStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Booking must be CONFIRMED before pickup. Current status: " + booking.getBookingStatus());
        }

        booking.setBookingStatus(BookingStatus.ACTIVE);
        booking.setPickupTime(LocalDateTime.now());

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
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        if (booking.getBookingStatus() != BookingStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE rentals can be returned. Current status: " + booking.getBookingStatus());
        }

        booking.setBookingStatus(BookingStatus.COMPLETED);
        booking.setReturnTime(LocalDateTime.now());

        if (request != null && request.getExtraCharges() != null && request.getExtraCharges().signum() > 0) {
            booking.setTotalAmount(booking.getTotalAmount().add(request.getExtraCharges()));
        }

        // Return Vehicle to AVAILABLE
        Vehicle vehicle = booking.getVehicle();
        vehicle.setStatus(VehicleStatus.AVAILABLE);
        if (request != null && request.getOdometerKm() != null) {
            vehicle.setCurrentOdometerKm(request.getOdometerKm());
        }
        vehicleRepository.save(vehicle);

        // Release Driver
        if (booking.getDriver() != null) {
            Driver driver = booking.getDriver();
            driver.setAvailable(true);
            driver.setTotalTrips(driver.getTotalTrips() + 1);
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
