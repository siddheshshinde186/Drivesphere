package com.drivesphere.service;

import com.drivesphere.dto.BookingDto;
import com.drivesphere.dto.RentalActionRequest;
import com.drivesphere.dto.ReviewCreateRequest;
import com.drivesphere.dto.ReviewDto;
import com.drivesphere.entity.*;
import com.drivesphere.mapper.EntityDtoMapper;
import com.drivesphere.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class BookingLifecycleAndRatingIntegrationTests {

        private BookingRepository bookingRepository;
        private VehicleRepository vehicleRepository;
        private DriverRepository driverRepository;
        private TripRepository tripRepository;
        private ReviewRepository reviewRepository;
        private UserRepository userRepository;
        private DriverTrustScoreService trustScoreService;
        private NotificationService notificationService;
        private VehicleService vehicleService;
        private EntityDtoMapper mapper;

        private RentalService rentalService;
        private ReviewService reviewService;

        private User customerA;
        private User customerB;
        private User driverUser;
        private Vehicle vehicle;
        private Driver driver;
        private Booking booking;
        private Trip trip;

        @BeforeEach
        void setUp() {
                bookingRepository = mock(BookingRepository.class);
                vehicleRepository = mock(VehicleRepository.class);
                driverRepository = mock(DriverRepository.class);
                tripRepository = mock(TripRepository.class);
                reviewRepository = mock(ReviewRepository.class);
                userRepository = mock(UserRepository.class);
                trustScoreService = mock(DriverTrustScoreService.class);
                notificationService = mock(NotificationService.class);
                vehicleService = mock(VehicleService.class);
                mapper = new EntityDtoMapper();

                rentalService = new RentalService(
                                bookingRepository,
                                vehicleRepository,
                                driverRepository,
                                tripRepository,
                                notificationService,
                                mapper);
                rentalService.setVehicleService(vehicleService);

                reviewService = new ReviewService(
                                reviewRepository,
                                bookingRepository,
                                vehicleRepository,
                                driverRepository,
                                userRepository,
                                trustScoreService,
                                mapper);

                customerA = User.builder()
                                .email("alice@customer.com")
                                .firstName("Alice")
                                .lastName("Walker")
                                .roles(Set.of(Role.builder().name(RoleType.ROLE_CUSTOMER).build()))
                                .build();
                customerA.setId(101L);

                customerB = User.builder()
                                .email("bob@customer.com")
                                .firstName("Bob")
                                .lastName("Builder")
                                .roles(Set.of(Role.builder().name(RoleType.ROLE_CUSTOMER).build()))
                                .build();
                customerB.setId(102L);

                driverUser = User.builder()
                                .firstName("Ramesh")
                                .lastName("Kumar")
                                .email("ramesh.driver@example.com")
                                .roles(Set.of(Role.builder().name(RoleType.ROLE_DRIVER).build()))
                                .build();
                driverUser.setId(500L);

                vehicle = Vehicle.builder()
                                .brand("Tata")
                                .model("Nexon EV")
                                .registrationNumber("KA01EV1001")
                                .status(VehicleStatus.AVAILABLE)
                                .available(true)
                                .rentalPricePerDay(new BigDecimal("2500.00"))
                                .currentOdometerKm(new BigDecimal("12000"))
                                .averageRating(null)
                                .totalRatings(0)
                                .build();
                vehicle.setId(1L);

                driver = Driver.builder()
                                .user(driverUser)
                                .licenseNumber("KA0120220009999")
                                .licenseExpiryDate(LocalDate.now().plusYears(3))
                                .online(true)
                                .available(true)
                                .totalTrips(10)
                                .rating(new BigDecimal("4.80"))
                                .build();
                driver.setId(501L);

                booking = Booking.builder()
                                .bookingReference("DS-20261101-LIFECYCLE")
                                .customer(customerA)
                                .vehicle(vehicle)
                                .driver(driver)
                                .bookingStatus(BookingStatus.CONFIRMED)
                                .paymentStatus(PaymentStatus.PAID)
                                .totalAmount(new BigDecimal("5000.00"))
                                .startDateTime(LocalDateTime.now().minusHours(2))
                                .endDateTime(LocalDateTime.now().plusDays(2))
                                .pickupLocation("Indiranagar Hub")
                                .dropLocation("Koramangala Hub")
                                .build();
                booking.setId(201L);

                trip = Trip.builder()
                                .booking(booking)
                                .status(TripStatus.NOT_STARTED)
                                .distanceKm(new BigDecimal("35.0"))
                                .build();
                trip.setId(301L);

                when(bookingRepository.findById(201L)).thenReturn(Optional.of(booking));
                when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
                when(vehicleRepository.findById(1L)).thenReturn(Optional.of(vehicle));
                when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));
                when(driverRepository.findById(501L)).thenReturn(Optional.of(driver));
                when(driverRepository.save(any(Driver.class))).thenAnswer(inv -> inv.getArgument(0));
                when(tripRepository.findByBookingId(201L)).thenReturn(Optional.of(trip));
                when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));
                when(userRepository.findById(101L)).thenReturn(Optional.of(customerA));
                when(userRepository.findById(102L)).thenReturn(Optional.of(customerB));
        }

        @Test
        @DisplayName("1. Pickup Transition: Customer starts trip -> booking ACTIVE, vehicle RENTED, trip IN_PROGRESS")
        void testPickupLifecycleTransition() {
                RentalActionRequest request = new RentalActionRequest();
                request.setOdometerKm(new BigDecimal("12050"));

                BookingDto result = rentalService.pickupVehicle(201L, request, customerA.getId(), false);

                assertNotNull(result);
                assertEquals(BookingStatus.ACTIVE, booking.getBookingStatus());
                assertEquals(VehicleStatus.RENTED, vehicle.getStatus());
                assertEquals(TripStatus.IN_PROGRESS, trip.getStatus());
                assertEquals(PaymentStatus.PAID, booking.getPaymentStatus(),
                                "Payment status must remain PAID independently");
        }

        @Test
        @DisplayName("2. Return Transition: Customer completes trip -> booking COMPLETED, vehicle status reconciled, trip COMPLETED")
        void testReturnLifecycleTransition() {
                // Precondition: booking is ACTIVE
                booking.setBookingStatus(BookingStatus.ACTIVE);
                vehicle.setStatus(VehicleStatus.RENTED);
                trip.setStatus(TripStatus.IN_PROGRESS);

                RentalActionRequest request = new RentalActionRequest();
                request.setOdometerKm(new BigDecimal("12150"));

                BookingDto result = rentalService.returnVehicle(201L, request, customerA.getId(), false);

                assertNotNull(result);
                assertEquals(BookingStatus.COMPLETED, booking.getBookingStatus());
                assertEquals(TripStatus.COMPLETED, trip.getStatus());
                assertEquals(new BigDecimal("12150"), vehicle.getCurrentOdometerKm());
                verify(vehicleService, times(1)).reconcileVehicleOperationalStatus(vehicle);
        }

        @Test
        @DisplayName("3. Security Authorization: Another customer (Customer B) cannot pickup or return Customer A's booking")
        void testSecurityAccessDeniedForOtherCustomer() {
                RentalActionRequest request = new RentalActionRequest();

                assertThrows(AccessDeniedException.class,
                                () -> rentalService.pickupVehicle(201L, request, customerB.getId(), false));

                booking.setBookingStatus(BookingStatus.ACTIVE);
                assertThrows(AccessDeniedException.class,
                                () -> rentalService.returnVehicle(201L, request, customerB.getId(), false));
        }

        @Test
        @DisplayName("3b. Future Booking Pickup Guard: Customer cannot start trip before permitted pickup window")
        void testCustomerCannotStartFutureBookingBeforeWindow() {
                // Scheduled in the future (e.g. 18 days later)
                booking.setStartDateTime(LocalDateTime.now().plusDays(18));
                booking.setEndDateTime(LocalDateTime.now().plusDays(25));

                RentalActionRequest request = new RentalActionRequest();

                IllegalStateException ex = assertThrows(IllegalStateException.class,
                                () -> rentalService.pickupVehicle(201L, request, customerA.getId(), false));
                assertTrue(ex.getMessage().contains("Trip cannot be started until permitted pickup window"),
                                "Exception should explain that pickup time has not arrived yet");
        }

        @Test
        @DisplayName("4. Rating Verification: Submitting detailed ratings (1-5 condition, cleanliness, comfort) on COMPLETED rental")
        void testSubmitReviewWithDetailedRatings() {
                booking.setBookingStatus(BookingStatus.COMPLETED);

                ReviewCreateRequest request = new ReviewCreateRequest();
                request.setBookingId(201L);
                request.setRating(new BigDecimal("5.0"));
                request.setVehicleRating(new BigDecimal("5.0"));
                request.setConditionRating(new BigDecimal("5.0"));
                request.setCleanlinessRating(new BigDecimal("4.0"));
                request.setComfortRating(new BigDecimal("5.0"));
                request.setDriverRating(new BigDecimal("5.0"));
                request.setComment("Incredible drive, spotless cabin and smooth acceleration!");

                List<Review> savedReviews = new ArrayList<>();
                when(reviewRepository.findByBookingId(201L)).thenReturn(Optional.empty());
                when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
                        Review r = inv.getArgument(0);
                        r.setId(999L);
                        r.setCreatedAt(Instant.now());
                        savedReviews.add(r);
                        return r;
                });
                when(reviewRepository.findByVehicleIdOrderByCreatedAtDesc(1L)).thenReturn(savedReviews);

                ReviewDto dto = reviewService.submitReview(customerA.getId(), request);

                assertNotNull(dto);
                assertEquals(new BigDecimal("5.0"), dto.getRating());
                assertEquals(new BigDecimal("5.0"), dto.getConditionRating());
                assertEquals(new BigDecimal("4.0"), dto.getCleanlinessRating());
                assertEquals(new BigDecimal("5.0"), dto.getComfortRating());
                assertEquals(new BigDecimal("5.00"), vehicle.getAverageRating());
                assertEquals(1, vehicle.getTotalRatings());
        }

        @Test
        @DisplayName("5. Non-Completed Guard: Cannot review a booking that is not COMPLETED (e.g. ACTIVE or CONFIRMED)")
        void testCannotReviewActiveOrConfirmedBooking() {
                booking.setBookingStatus(BookingStatus.ACTIVE);

                ReviewCreateRequest request = new ReviewCreateRequest();
                request.setBookingId(201L);
                request.setRating(new BigDecimal("5.0"));

                IllegalStateException ex = assertThrows(IllegalStateException.class,
                                () -> reviewService.submitReview(customerA.getId(), request));
                assertTrue(ex.getMessage().contains("Reviews can only be submitted for COMPLETED rentals"));
        }

        @Test
        @DisplayName("6. Single Review Enforcement: Duplicate review submissions for the same booking are rejected")
        void testDuplicateReviewRejected() {
                booking.setBookingStatus(BookingStatus.COMPLETED);

                Review existingReview = Review.builder()
                                .booking(booking)
                                .customer(customerA)
                                .vehicle(vehicle)
                                .rating(new BigDecimal("4.0"))
                                .build();
                existingReview.setId(888L);
                when(reviewRepository.findByBookingId(201L)).thenReturn(Optional.of(existingReview));

                ReviewCreateRequest request = new ReviewCreateRequest();
                request.setBookingId(201L);
                request.setRating(new BigDecimal("5.0"));

                IllegalStateException ex = assertThrows(IllegalStateException.class,
                                () -> reviewService.submitReview(customerA.getId(), request));
                assertTrue(ex.getMessage().contains("A review has already been submitted for this booking"));
        }

        @Test
        @DisplayName("7. Edit Review: Authenticated owner can edit their ratings and recalculate vehicle average")
        void testOwnerCanEditReviewAndRecalculate() {
                booking.setBookingStatus(BookingStatus.COMPLETED);

                Review existingReview = Review.builder()
                                .booking(booking)
                                .customer(customerA)
                                .vehicle(vehicle)
                                .rating(new BigDecimal("4.0"))
                                .vehicleRating(new BigDecimal("4.0"))
                                .conditionRating(new BigDecimal("4.0"))
                                .cleanlinessRating(new BigDecimal("3.0"))
                                .comfortRating(new BigDecimal("4.0"))
                                .comment("Good ride")
                                .build();
                existingReview.setId(888L);

                when(reviewRepository.findById(888L)).thenReturn(Optional.of(existingReview));
                when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

                List<Review> updatedList = List.of(existingReview);
                when(reviewRepository.findByVehicleIdOrderByCreatedAtDesc(1L)).thenReturn(updatedList);

                ReviewCreateRequest updateRequest = new ReviewCreateRequest();
                updateRequest.setRating(new BigDecimal("5.0"));
                updateRequest.setVehicleRating(new BigDecimal("5.0"));
                updateRequest.setConditionRating(new BigDecimal("5.0"));
                updateRequest.setCleanlinessRating(new BigDecimal("5.0"));
                updateRequest.setComfortRating(new BigDecimal("5.0"));
                updateRequest.setComment("Updated: Excellent experience after all!");

                ReviewDto updatedDto = reviewService.updateReview(888L, customerA.getId(), updateRequest);

                assertEquals(new BigDecimal("5.0"), updatedDto.getRating());
                assertEquals(new BigDecimal("5.0"), existingReview.getVehicleRating());
                assertEquals("Updated: Excellent experience after all!", existingReview.getComment());
                assertEquals(new BigDecimal("5.00"), vehicle.getAverageRating());
        }

        @Test
        @DisplayName("8. Edit Authorization: Customer B cannot edit Customer A's review")
        void testCustomerBCannotEditCustomerAReview() {
                Review existingReview = Review.builder()
                                .booking(booking)
                                .customer(customerA)
                                .vehicle(vehicle)
                                .rating(new BigDecimal("4.0"))
                                .build();
                existingReview.setId(888L);
                when(reviewRepository.findById(888L)).thenReturn(Optional.of(existingReview));

                ReviewCreateRequest updateRequest = new ReviewCreateRequest();
                updateRequest.setRating(new BigDecimal("5.0"));

                assertThrows(AccessDeniedException.class,
                                () -> reviewService.updateReview(888L, customerB.getId(), updateRequest));
        }
}
