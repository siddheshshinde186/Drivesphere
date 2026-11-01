package com.drivesphere.service;

import com.drivesphere.dto.BookingCreateRequest;
import com.drivesphere.dto.BookingDto;
import com.drivesphere.dto.ErrorResponse;
import com.drivesphere.entity.*;
import com.drivesphere.exception.GlobalExceptionHandler;
import com.drivesphere.integration.maps.MapsClient;
import com.drivesphere.mapper.EntityDtoMapper;
import com.drivesphere.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BookingTripCoordinatesTests {

    private BookingRepository bookingRepo;
    private VehicleRepository vehicleRepo;
    private DriverRepository driverRepo;
    private UserRepository userRepo;
    private TripRepository tripRepo;
    private MapsClient mapsClient;
    private NotificationService notificationService;
    private PaymentService paymentService;
    private EntityDtoMapper mapper;
    private BookingService bookingService;
    private GlobalExceptionHandler exceptionHandler;

    private User testCustomer;
    private Vehicle testVehicle;

    @BeforeEach
    void setUp() {
        bookingRepo = mock(BookingRepository.class);
        vehicleRepo = mock(VehicleRepository.class);
        driverRepo = mock(DriverRepository.class);
        userRepo = mock(UserRepository.class);
        tripRepo = mock(TripRepository.class);
        mapsClient = spy(new MapsClient());
        notificationService = mock(NotificationService.class);
        paymentService = mock(PaymentService.class);
        mapper = new EntityDtoMapper();
        exceptionHandler = new GlobalExceptionHandler();

        bookingService = new BookingService(
                bookingRepo,
                vehicleRepo,
                driverRepo,
                userRepo,
                tripRepo,
                notificationService,
                paymentService,
                mapsClient,
                mapper
        );

        testCustomer = User.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .phoneNumber("+919876543210")
                .active(true)
                .verified(true)
                .build();
        testCustomer.setId(10L);

        testVehicle = Vehicle.builder()
                .registrationNumber("KA-01-EV-2024")
                .brand("Tata")
                .model("Nexon EV Empowered")
                .rentalPricePerDay(BigDecimal.valueOf(2800.0))
                .rentalPricePerHour(BigDecimal.valueOf(220.0))
                .status(VehicleStatus.AVAILABLE)
                .available(true)
                .build();
        testVehicle.setId(1L);

        when(userRepo.findById(10L)).thenReturn(Optional.of(testCustomer));
        when(vehicleRepo.findById(1L)).thenReturn(Optional.of(testVehicle));
        when(vehicleRepo.countOverlappingVehicleBookings(any(), any(), any())).thenReturn(0L);

        when(bookingRepo.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(500L);
            return b;
        });

        when(tripRepo.save(any(Trip.class))).thenAnswer(inv -> {
            Trip t = inv.getArgument(0);
            t.setId(700L);
            return t;
        });
    }

    @Test
    @DisplayName("1. Booking succeeds with explicit valid coordinates, Trip created with non-null dest_latitude")
    void testCreateBooking_WithExplicitValidCoordinates_SucceedsAndPersistsTrip() {
        BookingCreateRequest request = BookingCreateRequest.builder()
                .vehicleId(1L)
                .startDateTime(LocalDateTime.now().plusDays(1))
                .endDateTime(LocalDateTime.now().plusDays(2))
                .pickupLocation("Custom Pickup")
                .dropLocation("Custom Drop")
                .pickupLatitude(BigDecimal.valueOf(12.9716))
                .pickupLongitude(BigDecimal.valueOf(77.5946))
                .dropLatitude(BigDecimal.valueOf(13.1986))
                .dropLongitude(BigDecimal.valueOf(77.7066))
                .build();

        BookingDto result = bookingService.createBooking(10L, request);

        assertNotNull(result);
        assertEquals(700L, result.getTripId());

        ArgumentCaptor<Trip> tripCaptor = ArgumentCaptor.forClass(Trip.class);
        verify(tripRepo).save(tripCaptor.capture());
        Trip savedTrip = tripCaptor.getValue();

        assertNotNull(savedTrip.getStartLatitude(), "Trip start_latitude must not be null");
        assertNotNull(savedTrip.getStartLongitude(), "Trip start_longitude must not be null");
        assertNotNull(savedTrip.getDestLatitude(), "Trip dest_latitude must not be null");
        assertNotNull(savedTrip.getDestLongitude(), "Trip dest_longitude must not be null");

        assertEquals(0, BigDecimal.valueOf(12.9716).compareTo(savedTrip.getStartLatitude()));
        assertEquals(0, BigDecimal.valueOf(13.1986).compareTo(savedTrip.getDestLatitude()));
        assertTrue(savedTrip.getDistanceKm().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("2. Booking succeeds with named locations ('Downtown Hub' and 'Airport Terminal') by resolving via MapsClient")
    void testCreateBooking_WithNamedLocations_ResolvesCoordinatesViaMapsClient() {
        BookingCreateRequest request = BookingCreateRequest.builder()
                .vehicleId(1L)
                .startDateTime(LocalDateTime.now().plusDays(1))
                .endDateTime(LocalDateTime.now().plusDays(2))
                .pickupLocation("Downtown Hub")
                .dropLocation("Airport Terminal")
                .pickupLatitude(null)
                .pickupLongitude(null)
                .dropLatitude(null)
                .dropLongitude(null)
                .build();

        BookingDto result = bookingService.createBooking(10L, request);

        assertNotNull(result);
        assertEquals(700L, result.getTripId());

        ArgumentCaptor<Trip> tripCaptor = ArgumentCaptor.forClass(Trip.class);
        verify(tripRepo).save(tripCaptor.capture());
        Trip savedTrip = tripCaptor.getValue();

        assertNotNull(savedTrip.getStartLatitude(), "Resolved start_latitude must not be null");
        assertNotNull(savedTrip.getDestLatitude(), "Resolved dest_latitude must not be null");
        assertEquals(0, BigDecimal.valueOf(12.9716).compareTo(savedTrip.getStartLatitude()));
        assertEquals(0, BigDecimal.valueOf(13.1986).compareTo(savedTrip.getDestLatitude()));
    }

    @Test
    @DisplayName("3. Booking with unresolvable destination coordinates throws IllegalArgumentException without saving Trip")
    void testCreateBooking_WithUnresolvableDestination_ThrowsIllegalArgumentException() {
        doThrow(new IllegalArgumentException("I couldn't locate 'UnknownFakeLocationXYZ9999'. Please provide a more specific location."))
                .when(mapsClient).geocode("UnknownFakeLocationXYZ9999");

        BookingCreateRequest request = BookingCreateRequest.builder()
                .vehicleId(1L)
                .startDateTime(LocalDateTime.now().plusDays(1))
                .endDateTime(LocalDateTime.now().plusDays(2))
                .pickupLocation("Downtown Hub")
                .dropLocation("UnknownFakeLocationXYZ9999")
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                bookingService.createBooking(10L, request)
        );

        assertTrue(ex.getMessage().contains("Unable to resolve destination location") ||
                ex.getMessage().contains("UnknownFakeLocationXYZ9999"));
        verify(tripRepo, never()).save(any());
        verify(bookingRepo, never()).save(any());
    }

    @Test
    @DisplayName("4. Booking with unresolvable pickup coordinates throws IllegalArgumentException without saving Trip")
    void testCreateBooking_WithUnresolvablePickup_ThrowsIllegalArgumentException() {
        doThrow(new IllegalArgumentException("I couldn't locate 'InvalidNonExistentOrigin9999'."))
                .when(mapsClient).geocode("InvalidNonExistentOrigin9999");

        BookingCreateRequest request = BookingCreateRequest.builder()
                .vehicleId(1L)
                .startDateTime(LocalDateTime.now().plusDays(1))
                .endDateTime(LocalDateTime.now().plusDays(2))
                .pickupLocation("InvalidNonExistentOrigin9999")
                .dropLocation("Airport Terminal")
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                bookingService.createBooking(10L, request)
        );

        assertTrue(ex.getMessage().contains("Unable to resolve pickup location") ||
                ex.getMessage().contains("InvalidNonExistentOrigin9999"));
        verify(tripRepo, never()).save(any());
        verify(bookingRepo, never()).save(any());
    }

    @Test
    @DisplayName("5. GlobalExceptionHandler: Column 'dest_latitude' cannot be null returns 400 Bad Request and does NOT claim account exists")
    void testGlobalExceptionHandler_NonAccountConstraint_DoesNotClaimAccountExists() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/bookings");
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "could not execute statement [Column 'dest_latitude' cannot be null] [insert into trips ...]"
        );

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleDataIntegrityViolationException(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("REQUIRED_FIELD_MISSING", response.getBody().getCode());
        assertFalse(response.getBody().getMessage().toLowerCase().contains("account"),
                "Non-user database error must NOT say 'An account with these details already exists'");
        assertTrue(response.getBody().getMessage().contains("cannot be null") ||
                response.getBody().getMessage().contains("required"));
    }

    @Test
    @DisplayName("6. GlobalExceptionHandler: Genuine duplicate account registration returns 409 Conflict with proper message")
    void testGlobalExceptionHandler_GenuineDuplicateAccount_ReturnsConflict() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "Duplicate entry 'test@example.com' for key 'users.email'"
        );

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleDataIntegrityViolationException(ex, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("EMAIL_ALREADY_REGISTERED", response.getBody().getCode());
        assertTrue(response.getBody().getMessage().contains("An account with this email address already exists."));
    }
}
