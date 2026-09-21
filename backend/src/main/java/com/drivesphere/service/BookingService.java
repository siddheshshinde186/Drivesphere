package com.drivesphere.service;

import com.drivesphere.dto.BookingCreateRequest;
import com.drivesphere.dto.BookingDto;
import com.drivesphere.entity.*;
import com.drivesphere.exception.ResourceNotFoundException;
import com.drivesphere.mapper.EntityDtoMapper;
import com.drivesphere.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final NotificationService notificationService;
    private final EntityDtoMapper mapper;

    @Transactional
    public BookingDto createBooking(Long customerId, BookingCreateRequest request) {
        if (request.getEndDateTime().isBefore(request.getStartDateTime())) {
            throw new IllegalArgumentException("End date time must be after start date time");
        }

        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + customerId));

        Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + request.getVehicleId()));

        if (vehicle.getStatus() == VehicleStatus.MAINTENANCE) {
            throw new IllegalStateException("Vehicle " + vehicle.getBrand() + " " + vehicle.getModel() + " is currently under maintenance and cannot be booked.");
        }
        if (vehicle.getStatus() == VehicleStatus.INACTIVE || !vehicle.isAvailable()) {
            throw new IllegalStateException("Vehicle is not available for booking.");
        }

        // Prevent double booking for vehicle
        long vehicleConflicts = vehicleRepository.countOverlappingVehicleBookings(
                vehicle.getId(), request.getStartDateTime(), request.getEndDateTime()
        );
        if (vehicleConflicts > 0) {
            throw new IllegalStateException("Selected vehicle is already booked for the specified dates.");
        }

        Driver driver = null;
        if (request.getDriverId() != null) {
            driver = driverRepository.findById(request.getDriverId())
                    .orElseThrow(() -> new ResourceNotFoundException("Driver not found with id: " + request.getDriverId()));

            if (driver.getVerification() == null || driver.getVerification().getStatus() != VerificationStatus.APPROVED) {
                throw new IllegalStateException("Selected driver is not yet verified and cannot be hired.");
            }
            if (!driver.isAvailable()) {
                throw new IllegalStateException("Selected driver is currently unavailable.");
            }

            long driverConflicts = driverRepository.countOverlappingDriverBookings(
                    driver.getId(), request.getStartDateTime(), request.getEndDateTime()
            );
            if (driverConflicts > 0) {
                throw new IllegalStateException("Selected driver is already assigned to another trip during this period.");
            }
        }

        // Calculate Duration & Pricing
        Duration duration = Duration.between(request.getStartDateTime(), request.getEndDateTime());
        long hours = Math.max(1, duration.toHours());
        int days = (int) Math.max(1, Math.ceil(hours / 24.0));

        BigDecimal rentalAmount = vehicle.getRentalPricePerDay().multiply(BigDecimal.valueOf(days));
        BigDecimal driverAmount = driver != null
                ? driver.getDailyRate().multiply(BigDecimal.valueOf(days))
                : BigDecimal.ZERO;

        BigDecimal subtotal = rentalAmount.add(driverAmount);
        BigDecimal taxAmount = subtotal.multiply(BigDecimal.valueOf(0.18)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = subtotal.add(taxAmount).setScale(2, RoundingMode.HALF_UP);

        String reference = "DS-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        Booking booking = Booking.builder()
                .bookingReference(reference)
                .customer(customer)
                .vehicle(vehicle)
                .driver(driver)
                .startDateTime(request.getStartDateTime())
                .endDateTime(request.getEndDateTime())
                .pickupLocation(request.getPickupLocation())
                .dropLocation(request.getDropLocation())
                .pickupLatitude(request.getPickupLatitude() != null ? request.getPickupLatitude() : vehicle.getLatitude())
                .pickupLongitude(request.getPickupLongitude() != null ? request.getPickupLongitude() : vehicle.getLongitude())
                .dropLatitude(request.getDropLatitude() != null ? request.getDropLatitude() : BigDecimal.valueOf(13.0827))
                .dropLongitude(request.getDropLongitude() != null ? request.getDropLongitude() : BigDecimal.valueOf(80.2707))
                .totalDays(days)
                .totalHours((int) hours)
                .rentalAmount(rentalAmount)
                .driverAmount(driverAmount)
                .taxAmount(taxAmount)
                .totalAmount(totalAmount)
                .bookingStatus(BookingStatus.PENDING)
                .paymentStatus(PaymentStatus.PENDING)
                .notes(request.getNotes())
                .build();

        booking = bookingRepository.save(booking);

        // Pre-create Trip linked to this booking
        Trip trip = Trip.builder()
                .booking(booking)
                .startLatitude(booking.getPickupLatitude())
                .startLongitude(booking.getPickupLongitude())
                .destLatitude(booking.getDropLatitude())
                .destLongitude(booking.getDropLongitude())
                .currentLatitude(booking.getPickupLatitude())
                .currentLongitude(booking.getPickupLongitude())
                .distanceKm(BigDecimal.valueOf(350.0)) // Default estimation, refined by routing
                .distanceTraveledKm(BigDecimal.ZERO)
                .status(TripStatus.NOT_STARTED)
                .build();
        tripRepository.save(trip);

        // Send confirmation alert
        notificationService.createNotification(
                customer,
                "Booking Created: " + booking.getBookingReference(),
                "Your booking for " + vehicle.getBrand() + " " + vehicle.getModel() + " has been initiated. Complete payment to confirm reservation.",
                NotificationType.BOOKING,
                "BOOKING",
                booking.getId()
        );

        BookingDto dto = mapper.toBookingDto(booking);
        dto.setTripId(trip.getId());
        return dto;
    }

    @Transactional
    public BookingDto cancelBooking(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        boolean isCustomer = booking.getCustomer().getId().equals(userId);
        boolean isAdmin = user.hasRole(RoleType.ROLE_ADMIN) || user.hasRole(RoleType.ROLE_FLEET_MANAGER);

        if (!isCustomer && !isAdmin) {
            throw new IllegalStateException("Unauthorized to cancel this booking.");
        }

        if (booking.getBookingStatus() == BookingStatus.COMPLETED || booking.getBookingStatus() == BookingStatus.ACTIVE) {
            throw new IllegalStateException("Cannot cancel a rental that is active or already completed.");
        }

        booking.setBookingStatus(BookingStatus.CANCELLED);
        if (booking.getPaymentStatus() == PaymentStatus.PAID) {
            booking.setPaymentStatus(PaymentStatus.REFUNDED);
        }

        // Release vehicle
        booking.getVehicle().setStatus(VehicleStatus.AVAILABLE);
        vehicleRepository.save(booking.getVehicle());

        // Release driver
        if (booking.getDriver() != null) {
            booking.getDriver().setAvailable(true);
            driverRepository.save(booking.getDriver());
        }

        notificationService.createNotification(
                booking.getCustomer(),
                "Booking Cancelled: " + booking.getBookingReference(),
                "Your booking has been cancelled successfully.",
                NotificationType.BOOKING,
                "BOOKING",
                booking.getId()
        );

        return mapper.toBookingDto(bookingRepository.save(booking));
    }

    @Transactional(readOnly = true)
    public List<BookingDto> getCustomerBookings(Long customerId) {
        return bookingRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(mapper::toBookingDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BookingDto> getDriverBookings(Long driverId) {
        return bookingRepository.findByDriverIdOrderByCreatedAtDesc(driverId).stream()
                .map(mapper::toBookingDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BookingDto> getAllBookings() {
        return bookingRepository.findAll().stream()
                .map(mapper::toBookingDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BookingDto getBookingById(Long id) {
        return bookingRepository.findById(id)
                .map(mapper::toBookingDto)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + id));
    }
}
