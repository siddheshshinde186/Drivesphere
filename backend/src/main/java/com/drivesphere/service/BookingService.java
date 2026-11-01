package com.drivesphere.service;

import com.drivesphere.dto.BookingCreateRequest;
import com.drivesphere.dto.BookingDto;
import com.drivesphere.entity.*;
import com.drivesphere.exception.ResourceNotFoundException;
import com.drivesphere.integration.maps.MapsClient;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.drivesphere.repository.MaintenanceRepository;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final NotificationService notificationService;
    private final PaymentService paymentService;
    private final MapsClient mapsClient;
    private final EntityDtoMapper mapper;
    private final MaintenanceRepository maintenanceRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @Autowired(required = false)
    private VehicleService vehicleService;

    @Autowired(required = false)
    private MaintenanceService maintenanceService;

    public void setVehicleService(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    public void setMaintenanceService(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @Autowired
    public BookingService(BookingRepository bookingRepository, VehicleRepository vehicleRepository, DriverRepository driverRepository, UserRepository userRepository, TripRepository tripRepository, NotificationService notificationService, PaymentService paymentService, MapsClient mapsClient, EntityDtoMapper mapper, @Autowired(required = false) MaintenanceRepository maintenanceRepository) {
        this.bookingRepository = bookingRepository;
        this.vehicleRepository = vehicleRepository;
        this.driverRepository = driverRepository;
        this.userRepository = userRepository;
        this.tripRepository = tripRepository;
        this.notificationService = notificationService;
        this.paymentService = paymentService;
        this.mapsClient = mapsClient;
        this.mapper = mapper;
        this.maintenanceRepository = maintenanceRepository;
    }

    public BookingService(BookingRepository bookingRepository, VehicleRepository vehicleRepository, DriverRepository driverRepository, UserRepository userRepository, TripRepository tripRepository, NotificationService notificationService, PaymentService paymentService, MapsClient mapsClient, EntityDtoMapper mapper) {
        this(bookingRepository, vehicleRepository, driverRepository, userRepository, tripRepository, notificationService, paymentService, mapsClient, mapper, null);
    }

    private static BigDecimal calculateHaversineDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double straightDistance = R * c;
        double estimatedRoadDistance = straightDistance * 1.25;
        return BigDecimal.valueOf(Math.max(1.0, estimatedRoadDistance)).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public BookingDto createBooking(Long customerId, BookingCreateRequest request) {
        long checkoutStart = System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now();
        if (request.getStartDateTime() == null || request.getEndDateTime() == null) {
            throw new IllegalArgumentException("Start date-time and End date-time are required.");
        }
        if (request.getStartDateTime().isBefore(now.minusMinutes(10))) {
            throw new IllegalArgumentException("Start date and time cannot be in the past.");
        }
        if (request.getEndDateTime().isBefore(request.getStartDateTime()) || request.getEndDateTime().isEqual(request.getStartDateTime())) {
            throw new IllegalArgumentException("End date and time must be strictly after start date and time.");
        }


        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + customerId));

        Vehicle vehicle = vehicleRepository.findByIdWithLock(request.getVehicleId())
                .or(() -> vehicleRepository.findById(request.getVehicleId()))
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + request.getVehicleId()));

        if (vehicle.getStatus() == VehicleStatus.INACTIVE || (!vehicle.isAvailable() && vehicle.getStatus() == VehicleStatus.AVAILABLE)) {
            throw new IllegalStateException("Vehicle is not available for booking.");
        }
        if (vehicle.getStatus() == VehicleStatus.MAINTENANCE) {
            throw new IllegalStateException("Vehicle " + vehicle.getBrand() + " " + vehicle.getModel() + " is currently under maintenance and cannot be booked.");
        }
        if (maintenanceService != null && maintenanceService.calculateServiceStatus(vehicle) == com.drivesphere.entity.VehicleServiceStatus.OVERDUE) {
            throw new IllegalStateException("Vehicle " + vehicle.getBrand() + " " + vehicle.getModel() + " has overdue maintenance and is currently restricted from new bookings.");
        }

        List<Booking> activeRentals = bookingRepository.findByVehicleIdAndBookingStatusIn(
                vehicle.getId(), List.of(BookingStatus.ACTIVE));
        boolean ongoingRentalConflict = activeRentals.stream().anyMatch(b ->
                b.getEndDateTime() != null && b.getEndDateTime().isAfter(request.getStartDateTime())
        );
        if (ongoingRentalConflict) {
            throw new IllegalStateException("Selected vehicle is currently on an active rental that extends past the requested pickup time.");
        }

        // Prevent double booking for vehicle (Concurrency Check)
        long vehicleConflicts = vehicleRepository.countOverlappingVehicleBookings(
                vehicle.getId(), request.getStartDateTime(), request.getEndDateTime()
        );
        if (vehicleConflicts > 0) {
            throw new IllegalStateException("Selected vehicle is already booked for the specified dates.");
        }

        // Prevent booking if vehicle has maintenance scheduled during the requested rental period
        if (maintenanceRepository != null) {
            long maintenanceConflicts = maintenanceRepository.countByVehicleIdAndStatusInAndServiceDateBetween(
                    vehicle.getId(),
                    List.of(MaintenanceStatus.SCHEDULED, MaintenanceStatus.IN_PROGRESS),
                    request.getStartDateTime().toLocalDate(),
                    request.getEndDateTime().toLocalDate()
            );
            if (maintenanceConflicts > 0) {
                throw new IllegalStateException("Selected vehicle has scheduled maintenance during the requested rental period.");
            }
        }

        Driver driver = null;
        if (request.getDriverId() != null) {
            driver = driverRepository.findById(request.getDriverId())
                    .orElseThrow(() -> new ResourceNotFoundException("Driver not found with id: " + request.getDriverId()));

            if (driver.getUser() == null || !driver.getUser().isActive()) {
                throw new IllegalStateException("Selected driver is no longer active.");
            }

            if (driver.getVerification() == null || driver.getVerification().getStatus() != VerificationStatus.APPROVED) {
                throw new IllegalStateException("Selected driver is not yet verified and cannot be hired.");
            }

            LocalDateTime threshold = LocalDateTime.now().minusSeconds(120);
            if (!driver.isOnline() || driver.getLastSeenAt() == null || driver.getLastSeenAt().isBefore(threshold)) {
                throw new IllegalStateException("Selected driver is no longer available: driver is offline or has a stale session.");
            }

            if (!driver.isAvailable()) {
                throw new IllegalStateException("Selected driver is no longer available (currently busy).");
            }

            long driverConflicts = driverRepository.countOverlappingDriverBookings(
                    driver.getId(), request.getStartDateTime(), request.getEndDateTime()
            );
            if (driverConflicts > 0) {
                throw new IllegalStateException("Selected driver is already assigned to another trip during this period.");
            }
        }

        // Calculate Duration & Hourly Pricing
        Duration duration = Duration.between(request.getStartDateTime(), request.getEndDateTime());
        long totalHours = Math.max(1, duration.toHours());
        int days = (int) (totalHours / 24);
        long remainingHours = totalHours % 24;

        BigDecimal vehicleHourlyRate = vehicle.getRentalPricePerDay().divide(BigDecimal.valueOf(24), 4, RoundingMode.HALF_UP);
        BigDecimal rentalAmount;
        if (days == 0) {
            rentalAmount = vehicleHourlyRate.multiply(BigDecimal.valueOf(totalHours)).setScale(2, RoundingMode.HALF_UP);
        } else {
            rentalAmount = vehicle.getRentalPricePerDay().multiply(BigDecimal.valueOf(days))
                    .add(vehicleHourlyRate.multiply(BigDecimal.valueOf(remainingHours)))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal driverAmount = BigDecimal.ZERO;
        if (driver != null) {
            BigDecimal driverHourlyRate = driver.getDailyRate().divide(BigDecimal.valueOf(24), 4, RoundingMode.HALF_UP);
            if (days == 0) {
                driverAmount = driverHourlyRate.multiply(BigDecimal.valueOf(totalHours)).setScale(2, RoundingMode.HALF_UP);
            } else {
                driverAmount = driver.getDailyRate().multiply(BigDecimal.valueOf(days))
                        .add(driverHourlyRate.multiply(BigDecimal.valueOf(remainingHours)))
                        .setScale(2, RoundingMode.HALF_UP);
            }
        }

        BigDecimal subtotal = rentalAmount.add(driverAmount);
        BigDecimal taxAmount = subtotal.multiply(BigDecimal.valueOf(0.18)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = subtotal.add(taxAmount).setScale(2, RoundingMode.HALF_UP);

        String reference = "DS-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        // Build Immutable Pricing Snapshot
        Map<String, Object> snapshotMap = new HashMap<>();
        snapshotMap.put("days", days);
        snapshotMap.put("hours", totalHours);
        snapshotMap.put("vehicleDailyRate", vehicle.getRentalPricePerDay());
        snapshotMap.put("rentalAmount", rentalAmount);
        snapshotMap.put("driverDailyRate", driver != null ? driver.getDailyRate() : BigDecimal.ZERO);
        snapshotMap.put("driverAmount", driverAmount);
        snapshotMap.put("subtotal", subtotal);
        snapshotMap.put("taxRate", 0.18);
        snapshotMap.put("taxAmount", taxAmount);
        snapshotMap.put("totalAmount", totalAmount);

        String snapshotJson = "";
        try {
            snapshotJson = objectMapper.writeValueAsString(snapshotMap);
        } catch (Exception e) {
            log.warn("Failed to serialize pricing snapshot: {}", e.getMessage());
        }

        // 1. Authoritative Coordinate Resolution for Pickup Location
        BigDecimal pickupLat = request.getPickupLatitude();
        BigDecimal pickupLng = request.getPickupLongitude();
        if (pickupLat == null || pickupLng == null) {
            String pickupLoc = request.getPickupLocation();
            if (pickupLoc == null || pickupLoc.trim().isBlank()) {
                throw new IllegalArgumentException("Pickup location or coordinates are required.");
            }
            if (mapsClient != null) {
                try {
                    com.drivesphere.integration.maps.MapsClient.GeocodeResult geo = mapsClient.geocode(pickupLoc.trim());
                    if (geo != null) {
                        pickupLat = BigDecimal.valueOf(geo.getLat()).setScale(7, RoundingMode.HALF_UP);
                        pickupLng = BigDecimal.valueOf(geo.getLng()).setScale(7, RoundingMode.HALF_UP);
                    }
                } catch (IllegalArgumentException iae) {
                    throw iae;
                } catch (Exception e) {
                    log.warn("Geocoding failed for pickup location '{}': {}", pickupLoc, e.getMessage());
                    throw new IllegalArgumentException("Unable to resolve pickup location: " + pickupLoc + ". Please provide a valid location or coordinates.");
                }
            }
        }

        if (pickupLat == null || pickupLng == null) {
            throw new IllegalArgumentException("Valid pickup coordinates could not be resolved for: " + request.getPickupLocation());
        }
        double pLat = pickupLat.doubleValue();
        double pLng = pickupLng.doubleValue();
        if (pLat < -90.0 || pLat > 90.0 || pLng < -180.0 || pLng > 180.0) {
            throw new IllegalArgumentException("Pickup coordinates out of geographical bounds.");
        }

        // 2. Authoritative Coordinate Resolution for Dropoff Location
        BigDecimal dropLat = request.getDropLatitude();
        BigDecimal dropLng = request.getDropLongitude();
        if (dropLat == null || dropLng == null) {
            String dropLoc = request.getDropLocation();
            if (dropLoc == null || dropLoc.trim().isBlank()) {
                throw new IllegalArgumentException("Drop location or coordinates are required.");
            }
            if (mapsClient != null) {
                try {
                    com.drivesphere.integration.maps.MapsClient.GeocodeResult geo = mapsClient.geocode(dropLoc.trim());
                    if (geo != null) {
                        dropLat = BigDecimal.valueOf(geo.getLat()).setScale(7, RoundingMode.HALF_UP);
                        dropLng = BigDecimal.valueOf(geo.getLng()).setScale(7, RoundingMode.HALF_UP);
                    }
                } catch (IllegalArgumentException iae) {
                    throw iae;
                } catch (Exception e) {
                    log.warn("Geocoding failed for drop location '{}': {}", dropLoc, e.getMessage());
                    throw new IllegalArgumentException("Unable to resolve destination location: " + dropLoc + ". Please provide a valid location or coordinates.");
                }
            }
        }

        if (dropLat == null || dropLng == null) {
            throw new IllegalArgumentException("Valid destination coordinates could not be resolved for: " + request.getDropLocation());
        }
        double dLat = dropLat.doubleValue();
        double dLng = dropLng.doubleValue();
        if (dLat < -90.0 || dLat > 90.0 || dLng < -180.0 || dLng > 180.0) {
            throw new IllegalArgumentException("Destination coordinates out of geographical bounds.");
        }

        LocalDateTime holdExpiresAt = LocalDateTime.now().plusMinutes(15);

        Booking booking = Booking.builder()
                .bookingReference(reference)
                .customer(customer)
                .vehicle(vehicle)
                .driver(driver)
                .startDateTime(request.getStartDateTime())
                .endDateTime(request.getEndDateTime())
                .pickupLocation(request.getPickupLocation())
                .dropLocation(request.getDropLocation())
                .pickupLatitude(pickupLat)
                .pickupLongitude(pickupLng)
                .dropLatitude(dropLat)
                .dropLongitude(dropLng)
                .totalDays(days)
                .totalHours((int) totalHours)
                .rentalAmount(rentalAmount)
                .driverAmount(driverAmount)
                .taxAmount(taxAmount)
                .taxRate(BigDecimal.valueOf(0.18))
                .discountAmount(BigDecimal.ZERO)
                .depositAmount(BigDecimal.ZERO)
                .totalAmount(totalAmount)
                .bookingStatus(BookingStatus.HELD)
                .paymentStatus(PaymentStatus.PENDING)
                .holdExpiresAt(holdExpiresAt)
                .pricingSnapshot(snapshotJson)
                .notes(request.getNotes())
                .build();

        booking = bookingRepository.save(booking);

        // Calculate dynamic road distance between resolved coordinates (sub-millisecond Haversine with road factor)
        BigDecimal distanceKm = calculateHaversineDistanceKm(pLat, pLng, dLat, dLng);

        // Pre-create Trip linked to this booking with verified coordinates and calculated distance
        Trip trip = Trip.builder()
                .booking(booking)
                .startLatitude(pickupLat)
                .startLongitude(pickupLng)
                .destLatitude(dropLat)
                .destLongitude(dropLng)
                .currentLatitude(pickupLat)
                .currentLongitude(pickupLng)
                .distanceKm(distanceKm)
                .distanceTraveledKm(BigDecimal.ZERO)
                .status(TripStatus.NOT_STARTED)
                .build();
        tripRepository.save(trip);

        // Send confirmation alert
        if (notificationService != null) {
            try {
                notificationService.createNotification(
                        customer,
                        "Booking Hold Initiated: " + booking.getBookingReference(),
                        "Your vehicle reservation for " + vehicle.getBrand() + " " + vehicle.getModel() + " is held for 15 minutes. Complete payment to confirm.",
                        NotificationType.BOOKING,
                        "BOOKING",
                        booking.getId()
                );
            } catch (Exception ne) {
                log.warn("Failed to dispatch booking notification: {}", ne.getMessage());
            }
        }

        long checkoutDuration = System.currentTimeMillis() - checkoutStart;
        log.info("[CHECKOUT_PERF] Booking hold created in {} ms (bookingRef: {}, customerId: {}, vehicleId: {})",
                checkoutDuration, booking.getBookingReference(), customerId, vehicle.getId());

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
            throw new org.springframework.security.access.AccessDeniedException("Unauthorized to cancel this booking.");
        }

        if (booking.getBookingStatus() == BookingStatus.COMPLETED || booking.getBookingStatus() == BookingStatus.ACTIVE) {
            throw new IllegalStateException("Cannot cancel a rental that is active or already completed.");
        }

        boolean wasPaid = booking.getPaymentStatus() == PaymentStatus.PAID;
        booking.setBookingStatus(BookingStatus.CANCELLED);

        if (wasPaid) {
            BigDecimal refundAmt = paymentService.calculateRefundAmount(booking);
            if (refundAmt.compareTo(BigDecimal.ZERO) > 0) {
                paymentService.processRefundForBooking(booking, refundAmt, "Booking cancellation refund");
                booking.setPaymentStatus(PaymentStatus.REFUNDED);
            } else {
                log.info("Cancellation policy: 0% refund for booking cancelled less than 2 hours before trip start.");
                booking.setPaymentStatus(PaymentStatus.PAID);
            }
        } else {
            booking.setPaymentStatus(PaymentStatus.FAILED);
        }

        // Release vehicle through centralized operational status reconciliation
        if (booking.getVehicle() != null) {
            if (vehicleService != null) {
                vehicleService.reconcileVehicleOperationalStatus(booking.getVehicle());
            } else {
                booking.getVehicle().setStatus(VehicleStatus.AVAILABLE);
                booking.getVehicle().setAvailable(true);
                vehicleRepository.save(booking.getVehicle());
            }
        }

        // Release driver: Only set available=true if driver is actually online!
        if (booking.getDriver() != null) {
            Driver d = booking.getDriver();
            d.setAvailable(d.isOnline());
            driverRepository.save(d);
        }

        notificationService.createNotification(
                booking.getCustomer(),
                "Booking Cancelled: " + booking.getBookingReference(),
                wasPaid ? "Your booking has been cancelled and refund policy applied."
                        : "Your booking has been cancelled successfully.",
                NotificationType.BOOKING,
                "BOOKING",
                booking.getId()
        );

        return mapper.toBookingDto(bookingRepository.save(booking));
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 60000)
    @Transactional
    public void cleanupExpiredHolds() {
        List<BookingStatus> holdStatuses = List.of(BookingStatus.HELD, BookingStatus.PENDING, BookingStatus.PENDING_PAYMENT);
        List<Booking> expiredBookings = bookingRepository.findByBookingStatusInAndHoldExpiresAtBefore(
                holdStatuses, LocalDateTime.now()
        );

        for (Booking b : expiredBookings) {
            if (b.getPaymentStatus() != PaymentStatus.PAID) {
                log.info("Cleaning up expired booking hold: {}", b.getBookingReference());
                b.setBookingStatus(BookingStatus.EXPIRED);
                if (b.getVehicle() != null) {
                    if (vehicleService != null) {
                        vehicleService.reconcileVehicleOperationalStatus(b.getVehicle());
                    } else if (b.getVehicle().getStatus() == VehicleStatus.BOOKED) {
                        b.getVehicle().setStatus(VehicleStatus.AVAILABLE);
                        b.getVehicle().setAvailable(true);
                        vehicleRepository.save(b.getVehicle());
                    }
                }
                if (b.getDriver() != null) {
                    b.getDriver().setAvailable(b.getDriver().isOnline());
                    driverRepository.save(b.getDriver());
                }
                bookingRepository.save(b);

                notificationService.createNotification(
                        b.getCustomer(),
                        "Reservation Hold Expired",
                        "Your 15-minute reservation hold on booking " + b.getBookingReference() + " has expired. Please initiate a new booking if you still wish to rent this vehicle.",
                        NotificationType.BOOKING,
                        "BOOKING",
                        b.getId()
                );
            }
        }
    }

    @Transactional
    public List<BookingDto> getCustomerBookings(Long customerId) {
        List<Booking> bookings = bookingRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
        LocalDateTime now = LocalDateTime.now();
        for (Booking b : bookings) {
            checkAndActivateBookingIfDue(b, now);
        }
        return bookings.stream()
                .map(mapper::toBookingDto)
                .collect(Collectors.toList());
    }

    private void checkAndActivateBookingIfDue(Booking b, LocalDateTime now) {
        if (b == null) return;
        if (b.getBookingStatus() == BookingStatus.CONFIRMED
                && b.getPaymentStatus() == PaymentStatus.PAID
                && b.getStartDateTime() != null
                && !now.isBefore(b.getStartDateTime())
                && (b.getEndDateTime() == null || now.isBefore(b.getEndDateTime()))) {
            b.setBookingStatus(BookingStatus.ACTIVE);
            if (b.getPickupTime() == null) {
                b.setPickupTime(now);
            }
            if (b.getVehicle() != null) {
                b.getVehicle().setStatus(VehicleStatus.RENTED);
                b.getVehicle().setAvailable(false);
                vehicleRepository.save(b.getVehicle());
            }
            if (tripRepository != null) {
                tripRepository.findByBookingId(b.getId()).ifPresent(trip -> {
                    if (trip.getStatus() == TripStatus.NOT_STARTED) {
                        trip.setStatus(TripStatus.IN_PROGRESS);
                        trip.setStartTime(now);
                        tripRepository.save(trip);
                    }
                });
            }
            bookingRepository.save(b);
        }
    }

    @Transactional(readOnly = true)
    public List<BookingDto> getDriverBookings(Long driverId) {
        return getDriverBookings(driverId, null, true);
    }

    @Transactional(readOnly = true)
    public List<BookingDto> getDriverBookings(Long driverId, Long currentUserId, boolean isStaff) {
        if (!isStaff) {
            Driver driver = driverRepository.findByUserId(currentUserId)
                    .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Driver profile not found."));
            if (!driver.getId().equals(driverId)) {
                throw new org.springframework.security.access.AccessDeniedException("Drivers can only view their own assigned bookings.");
            }
        }
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

    @Transactional
    public BookingDto getBookingById(Long id, Long currentUserId, boolean isStaff) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + id));

        if (!isStaff) {
            boolean isCustomer = booking.getCustomer() != null && booking.getCustomer().getId().equals(currentUserId);
            boolean isDriver = booking.getDriver() != null && booking.getDriver().getUser() != null
                    && booking.getDriver().getUser().getId().equals(currentUserId);

            if (!isCustomer && !isDriver) {
                throw new org.springframework.security.access.AccessDeniedException("Access denied: You do not have authorization to view this booking.");
            }
        }

        checkAndActivateBookingIfDue(booking, LocalDateTime.now());
        return mapper.toBookingDto(booking);
    }

    @Transactional(readOnly = true)
    public BookingDto getBookingById(Long id) {
        return bookingRepository.findById(id)
                .map(mapper::toBookingDto)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + id));
    }

    /**
     * Automatic reconciliation of expired booking holds and pending payments.
     * Runs every 60 seconds to release reserved inventory back to the fleet.
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 60000)
    @Transactional
    public int expireStaleHolds() {
        LocalDateTime now = LocalDateTime.now();
        List<Booking> staleHolds = bookingRepository.findByBookingStatusInAndHoldExpiresAtBefore(
                List.of(BookingStatus.HELD, BookingStatus.PENDING, BookingStatus.PENDING_PAYMENT), now
        );
        for (Booking booking : staleHolds) {
            log.info("Expiring stale hold for booking ID {} (ref: {})", booking.getId(), booking.getBookingReference());
            booking.setBookingStatus(BookingStatus.CANCELLED);
            booking.setPaymentStatus(PaymentStatus.FAILED);
            bookingRepository.save(booking);
            if (booking.getVehicle() != null && vehicleService != null) {
                vehicleService.reconcileVehicleOperationalStatus(booking.getVehicle());
            }
        }
        return staleHolds.size();
    }
}
