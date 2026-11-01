package com.drivesphere.service;

import com.drivesphere.dto.*;
import com.drivesphere.entity.*;
import com.drivesphere.integration.cloudinary.CloudinaryClient;
import com.drivesphere.integration.razorpay.RazorpayClient;
import com.drivesphere.mapper.EntityDtoMapper;
import com.drivesphere.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SecurityRegressionTests {

    private DriverRepository driverRepo;
    private DriverVerificationRepository verificationRepo;
    private BookingRepository bookingRepo;
    private VehicleRepository vehicleRepo;
    private UserRepository userRepo;
    private TripRepository tripRepo;
    private PaymentRepository paymentRepo;
    private NotificationRepository notificationRepo;
    private ReviewRepository reviewRepo;
    private EntityDtoMapper mapper;

    @BeforeEach
    void setUp() {
        driverRepo = Mockito.mock(DriverRepository.class);
        verificationRepo = Mockito.mock(DriverVerificationRepository.class);
        bookingRepo = Mockito.mock(BookingRepository.class);
        vehicleRepo = Mockito.mock(VehicleRepository.class);
        userRepo = Mockito.mock(UserRepository.class);
        tripRepo = Mockito.mock(TripRepository.class);
        paymentRepo = Mockito.mock(PaymentRepository.class);
        notificationRepo = Mockito.mock(NotificationRepository.class);
        reviewRepo = Mockito.mock(ReviewRepository.class);
        mapper = new EntityDtoMapper();
    }

    private User createUser(Long id, String email, RoleType role) {
        User u = User.builder()
                .email(email)
                .emailVerified(true)
                .roles(Set.of(Role.builder().name(role).build()))
                .build();
        u.setId(id);
        return u;
    }

    // ==========================================
    // DRIVER AUTHORIZATION & DATA PRIVACY
    // ==========================================

    @Test
    @DisplayName("Driver A cannot update Driver B's profile")
    void driverCannotModifyAnotherDriverProfile() {
        DriverService driverService = new DriverService(driverRepo, mapper);

        User userB = createUser(200L, "driverB@example.com", RoleType.ROLE_DRIVER);
        Driver driverB = Driver.builder()
                .licenseNumber("DL-222")
                .experienceYears(3)
                .dailyRate(BigDecimal.valueOf(800.0))
                .user(userB)
                .build();
        driverB.setId(20L);

        when(driverRepo.findById(20L)).thenReturn(Optional.of(driverB));

        DriverDto updateRequest = DriverDto.builder().experienceYears(5).dailyRate(BigDecimal.valueOf(1000.0)).build();

        // Caller is User 100 (Driver A), trying to update Driver 20 (User 200)
        assertThrows(AccessDeniedException.class, () ->
                driverService.updateProfile(20L, updateRequest, 100L, false)
        );
    }

    @Test
    @DisplayName("Driver A cannot toggle Driver B's availability")
    void driverCannotToggleAnotherDriverAvailability() {
        DriverService driverService = new DriverService(driverRepo, mapper);

        User userB = createUser(200L, "driverB@example.com", RoleType.ROLE_DRIVER);
        Driver driverB = Driver.builder()
                .available(true)
                .user(userB)
                .build();
        driverB.setId(20L);

        when(driverRepo.findById(20L)).thenReturn(Optional.of(driverB));

        // Driver A (User 100) tries to toggle Driver B (User 200)
        assertThrows(AccessDeniedException.class, () ->
                driverService.toggleAvailability(20L, 100L, false)
        );
    }

    @Test
    @DisplayName("Public available drivers list strips sensitive personal data")
    void publicAvailableDriversSanitized() {
        DriverService driverService = new DriverService(driverRepo, mapper);

        User u = createUser(300L, "private_driver@example.com", RoleType.ROLE_DRIVER);
        u.setPhoneNumber("+91 9999988888");

        Driver driver = Driver.builder()
                .licenseNumber("SENSITIVE-DL-999")
                .address("Confidential Home Address")
                .dailyRate(BigDecimal.valueOf(900.0))
                .available(true)
                .user(u)
                .build();
        driver.setOnline(true);
        driver.setLastSeenAt(java.time.LocalDateTime.now());
        when(driverRepo.findAvailableOnlineAndApprovedDrivers(any())).thenReturn(List.of(driver));

        List<DriverDto> publicList = driverService.getAvailableAndApprovedDrivers();
        assertFalse(publicList.isEmpty());

        DriverDto publicDto = publicList.get(0);
        assertNull(publicDto.getEmail(), "Public driver profile must not leak email");
        assertNull(publicDto.getPhoneNumber(), "Public driver profile must not leak phone number");
        assertNull(publicDto.getLicenseNumber(), "Public driver profile must not leak license number");
        assertNull(publicDto.getAddress(), "Public driver profile must not leak home address");
        assertNull(publicDto.getUserId(), "Public driver profile must not leak user id");
    }

    // ==========================================
    // DRIVER VERIFICATION AUTHORIZATION
    // ==========================================

    @Test
    @DisplayName("Driver A cannot view Driver B's verification")
    void driverCannotAccessAnotherDriverVerification() {
        DriverVerificationService vService = new DriverVerificationService(verificationRepo, driverRepo, userRepo, null, null, mapper);

        User userB = createUser(200L, "driverB@example.com", RoleType.ROLE_DRIVER);
        Driver driverB = Driver.builder().user(userB).build();
        driverB.setId(20L);

        DriverVerification dv = DriverVerification.builder().driver(driverB).status(VerificationStatus.APPROVED).build();
        when(driverRepo.findById(20L)).thenReturn(Optional.of(driverB));
        when(verificationRepo.findByDriver(driverB)).thenReturn(Optional.of(dv));

        // Driver A (User 100) attempts to read Driver B (User 200) verification
        assertThrows(AccessDeniedException.class, () ->
                vService.getVerificationByDriverId(20L, 100L, false)
        );
    }

    @Test
    @DisplayName("Driver A cannot submit verification for Driver B")
    void driverCannotSubmitVerificationForAnotherDriver() {
        DriverVerificationService vService = new DriverVerificationService(verificationRepo, driverRepo, userRepo, null, null, mapper);

        User userB = createUser(200L, "driverB@example.com", RoleType.ROLE_DRIVER);
        Driver driverB = Driver.builder().user(userB).build();
        driverB.setId(20L);

        when(driverRepo.findById(20L)).thenReturn(Optional.of(driverB));

        DriverVerificationDto req = DriverVerificationDto.builder()
                .identityDocUrl("https://example.com/doc.pdf")
                .build();

        // Driver A (User 100) attempts to submit for Driver 20 (User 200)
        assertThrows(AccessDeniedException.class, () ->
                vService.submitVerificationDocuments(20L, req, 100L, false)
        );
    }

    // ==========================================
    // DOCUMENT DOWNLOAD SECURITY
    // ==========================================

    @Test
    @DisplayName("Customer cannot download driver verification documents")
    void customerCannotDownloadDriverDocuments() {
        CloudinaryClient cloudinary = Mockito.mock(CloudinaryClient.class);
        DocumentStorageService docService = new DocumentStorageService(cloudinary, verificationRepo);

        // Caller is ROLE_CUSTOMER (isCustomer=true, isAdmin=false)
        assertThrows(AccessDeniedException.class, () ->
                docService.loadFileAsResource("license_123.pdf", 400L, false, true)
        );
    }

    @Test
    @DisplayName("Driver A cannot download Driver B's verification documents")
    void driverCannotDownloadAnotherDriverDocuments() {
        CloudinaryClient cloudinary = Mockito.mock(CloudinaryClient.class);
        DocumentStorageService docService = new DocumentStorageService(cloudinary, verificationRepo);

        User userA = createUser(100L, "driverA@example.com", RoleType.ROLE_DRIVER);
        Driver driverA = Driver.builder().user(userA).build();
        driverA.setId(10L);

        User userB = createUser(200L, "driverB@example.com", RoleType.ROLE_DRIVER);
        Driver driverB = Driver.builder().user(userB).build();
        driverB.setId(20L);

        DriverVerification dvB = DriverVerification.builder()
                .driver(driverB)
                .licenseDocUrl("/api/documents/download/driverB_license.pdf")
                .build();

        when(verificationRepo.findAll()).thenReturn(List.of(dvB));

        // Driver A (User 100) attempts to download Driver B's document
        assertThrows(AccessDeniedException.class, () ->
                docService.loadFileAsResource("driverB_license.pdf", 100L, false, false)
        );
    }

    @Test
    @DisplayName("Admin can access and download any driver KYC document")
    void adminCanDownloadDriverVerificationDocuments() {
        CloudinaryClient cloudinary = Mockito.mock(CloudinaryClient.class);
        DocumentStorageService docService = new DocumentStorageService(cloudinary, verificationRepo);

        // Admin caller (isAdmin = true, isCustomer = false)
        // Passes authorization check and reaches storage lookup
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                docService.loadFileAsResource("driver_kyc.pdf", 1L, true, false)
        );
        assertTrue(ex.getMessage().contains("File not found"));
    }

    @Test
    @DisplayName("Owning Driver can access and download their own KYC document")
    void driverOwnerCanDownloadOwnVerificationDocuments() {
        CloudinaryClient cloudinary = Mockito.mock(CloudinaryClient.class);
        DocumentStorageService docService = new DocumentStorageService(cloudinary, verificationRepo);

        User userB = createUser(200L, "driverB@example.com", RoleType.ROLE_DRIVER);
        Driver driverB = Driver.builder().user(userB).build();
        driverB.setId(20L);

        DriverVerification dvB = DriverVerification.builder()
                .driver(driverB)
                .licenseDocUrl("/api/documents/download/driverB_license.pdf")
                .build();

        when(verificationRepo.findAll()).thenReturn(List.of(dvB));

        // Driver B (User 200) requests their own document -> authorization passes and reaches storage lookup
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                docService.loadFileAsResource("driverB_license.pdf", 200L, false, false)
        );
        assertTrue(ex.getMessage().contains("File not found"));
    }

    // ==========================================
    // BOOKING & RENTAL AUTHORIZATION
    // ==========================================

    @Test
    @DisplayName("Customer A cannot view Customer B's booking")
    void customerCannotViewAnotherCustomerBooking() {
        BookingService bookingService = new BookingService(bookingRepo, vehicleRepo, driverRepo, userRepo, tripRepo, null, null, null, mapper);

        User customerB = createUser(200L, "custB@example.com", RoleType.ROLE_CUSTOMER);
        Booking booking = Booking.builder().customer(customerB).build();
        booking.setId(50L);

        when(bookingRepo.findById(50L)).thenReturn(Optional.of(booking));

        // Customer A (User 100) attempts to view Booking 50 (owned by Customer B 200)
        assertThrows(AccessDeniedException.class, () ->
                bookingService.getBookingById(50L, 100L, false)
        );
    }

    @Test
    @DisplayName("Customer A cannot cancel Customer B's booking")
    void customerCannotCancelAnotherCustomerBooking() {
        BookingService bookingService = new BookingService(bookingRepo, vehicleRepo, driverRepo, userRepo, tripRepo, null, null, null, mapper);

        User customerB = createUser(200L, "custB@example.com", RoleType.ROLE_CUSTOMER);
        User customerA = createUser(100L, "custA@example.com", RoleType.ROLE_CUSTOMER);

        Booking booking = Booking.builder()
                .customer(customerB)
                .bookingStatus(BookingStatus.CONFIRMED)
                .build();
        booking.setId(50L);

        when(bookingRepo.findById(50L)).thenReturn(Optional.of(booking));
        when(userRepo.findById(100L)).thenReturn(Optional.of(customerA));

        // Customer A (User 100) attempts to cancel Booking 50
        assertThrows(AccessDeniedException.class, () ->
                bookingService.cancelBooking(50L, 100L)
        );
    }

    @Test
    @DisplayName("Driver A cannot perform pickup on Driver B's assigned booking")
    void driverCannotPickupAnotherDriverBooking() {
        RentalService rentalService = new RentalService(bookingRepo, vehicleRepo, driverRepo, tripRepo, null, mapper);

        User userB = createUser(200L, "driverB@example.com", RoleType.ROLE_DRIVER);
        Driver driverB = Driver.builder().user(userB).build();
        driverB.setId(20L);

        User customer = createUser(500L, "cust@example.com", RoleType.ROLE_CUSTOMER);

        Booking booking = Booking.builder()
                .driver(driverB)
                .bookingStatus(BookingStatus.CONFIRMED)
                .vehicle(Vehicle.builder().build())
                .customer(customer)
                .build();
        booking.setId(50L);

        when(bookingRepo.findById(50L)).thenReturn(Optional.of(booking));

        // Driver A (User 100) attempts pickup on Booking 50 (assigned to Driver B 200)
        assertThrows(AccessDeniedException.class, () ->
                rentalService.pickupVehicle(50L, null, 100L, false)
        );
    }

    @Test
    @DisplayName("Rental return rejects negative extra charges")
    void rentalReturnRejectsNegativeExtraCharges() {
        RentalService rentalService = new RentalService(bookingRepo, vehicleRepo, driverRepo, tripRepo, null, mapper);

        User userA = createUser(100L, "driverA@example.com", RoleType.ROLE_DRIVER);
        Driver driverA = Driver.builder().user(userA).build();
        driverA.setId(10L);

        User customer = createUser(500L, "cust@example.com", RoleType.ROLE_CUSTOMER);

        Booking booking = Booking.builder()
                .driver(driverA)
                .bookingStatus(BookingStatus.ACTIVE)
                .vehicle(Vehicle.builder().build())
                .customer(customer)
                .build();
        booking.setId(50L);

        when(bookingRepo.findById(50L)).thenReturn(Optional.of(booking));

        RentalActionRequest badRequest = RentalActionRequest.builder()
                .extraCharges(BigDecimal.valueOf(-250.0))
                .build();

        assertThrows(IllegalArgumentException.class, () ->
                rentalService.returnVehicle(50L, badRequest, 100L, false)
        );
    }

    // ==========================================
    // TRIP & GPS TELEMETRY AUTHORIZATION
    // ==========================================

    @Test
    @DisplayName("Customer A cannot view Customer B's trip telemetry")
    void customerCannotViewAnotherUserTrip() {
        TripTrackingService tripService = new TripTrackingService(tripRepo, null, vehicleRepo, mapper);

        User customerB = createUser(200L, "custB@example.com", RoleType.ROLE_CUSTOMER);
        Booking booking = Booking.builder().customer(customerB).build();
        Trip trip = Trip.builder().booking(booking).build();
        trip.setId(70L);

        when(tripRepo.findById(70L)).thenReturn(Optional.of(trip));

        // Customer A (User 100) tries to view Trip 70
        assertThrows(AccessDeniedException.class, () ->
                tripService.getTripById(70L, 100L, false)
        );
    }

    @Test
    @DisplayName("Driver A cannot update GPS telemetry on Driver B's trip")
    void driverCannotUpdateAnotherDriverTripGps() {
        TripTrackingService tripService = new TripTrackingService(tripRepo, null, vehicleRepo, mapper);

        User userB = createUser(200L, "driverB@example.com", RoleType.ROLE_DRIVER);
        Driver driverB = Driver.builder().user(userB).build();
        Booking booking = Booking.builder().driver(driverB).build();
        Trip trip = Trip.builder().booking(booking).status(TripStatus.IN_PROGRESS).build();
        trip.setId(70L);

        when(tripRepo.findById(70L)).thenReturn(Optional.of(trip));

        GpsUpdateDto gps = GpsUpdateDto.builder()
                .tripId(70L)
                .latitude(BigDecimal.valueOf(12.9352))
                .longitude(BigDecimal.valueOf(77.6245))
                .build();

        // Driver A (User 100) tries to update GPS on Trip 70
        assertThrows(AccessDeniedException.class, () ->
                tripService.updateGpsLocation(gps, 100L, false)
        );
    }

    @Test
    @DisplayName("GPS simulation is rejected when allow-simulation is disabled")
    void gpsSimulationDisabledInProduction() {
        TripTrackingService tripService = new TripTrackingService(tripRepo, null, vehicleRepo, mapper);
        ReflectionTestUtils.setField(tripService, "allowSimulation", false);

        assertThrows(IllegalStateException.class, () ->
                tripService.simulateTripStep(70L, 1L, true)
        );
    }

    // ==========================================
    // PAYMENT SECURITY
    // ==========================================

    @Test
    @DisplayName("Customer A cannot create payment order for Customer B's booking")
    void customerCannotCreatePaymentForAnotherBooking() {
        PaymentService paymentService = new PaymentService(bookingRepo, paymentRepo, vehicleRepo, driverRepo, null, null);

        User customerB = createUser(200L, "custB@example.com", RoleType.ROLE_CUSTOMER);
        Booking booking = Booking.builder().customer(customerB).build();
        booking.setId(50L);

        when(bookingRepo.findById(50L)).thenReturn(Optional.of(booking));

        // Customer A (User 100) tries to create order for Booking 50
        assertThrows(AccessDeniedException.class, () ->
                paymentService.createPaymentOrder(50L, 100L, false)
        );
    }

    @Test
    @DisplayName("Customer A cannot verify payment for Customer B's booking")
    void customerCannotVerifyPaymentForAnotherBooking() {
        PaymentService paymentService = new PaymentService(bookingRepo, paymentRepo, vehicleRepo, driverRepo, null, null);

        User customerB = createUser(200L, "custB@example.com", RoleType.ROLE_CUSTOMER);
        Booking booking = Booking.builder().customer(customerB).build();
        booking.setId(50L);

        when(bookingRepo.findById(50L)).thenReturn(Optional.of(booking));

        PaymentVerifyRequest req = PaymentVerifyRequest.builder()
                .bookingId(50L)
                .orderId("order_123")
                .paymentId("pay_123")
                .build();

        assertThrows(AccessDeniedException.class, () ->
                paymentService.verifyPayment(req, 100L, false)
        );
    }

    @Test
    @DisplayName("Payment verification rejects order ID belonging to a different booking")
    void paymentVerificationRejectsMismatchedBookingAndOrder() {
        PaymentService paymentService = new PaymentService(bookingRepo, paymentRepo, vehicleRepo, driverRepo, null, null);

        User customerA = createUser(100L, "custA@example.com", RoleType.ROLE_CUSTOMER);
        Booking booking50 = Booking.builder().customer(customerA).build();
        booking50.setId(50L);

        Booking booking99 = Booking.builder().customer(customerA).build();
        booking99.setId(99L);

        Payment paymentOther = Payment.builder()
                .booking(booking99)
                .gatewayOrderId("order_for_booking_99")
                .build();

        when(bookingRepo.findById(50L)).thenReturn(Optional.of(booking50));
        when(paymentRepo.findByGatewayOrderId("order_for_booking_99")).thenReturn(Optional.of(paymentOther));

        PaymentVerifyRequest req = PaymentVerifyRequest.builder()
                .bookingId(50L)
                .orderId("order_for_booking_99")
                .paymentId("pay_123")
                .build();

        assertThrows(IllegalArgumentException.class, () ->
                paymentService.verifyPayment(req, 100L, false)
        );
    }

    @Test
    @DisplayName("Client cannot bypass payment verification using mockApproval flag when mock is disabled")
    void clientMockApprovalCannotBypassInProduction() {
        RazorpayClient razorpayClient = new RazorpayClient();
        ReflectionTestUtils.setField(razorpayClient, "allowMock", false);

        PaymentService paymentService = new PaymentService(bookingRepo, paymentRepo, vehicleRepo, driverRepo, razorpayClient, null);

        User customerA = createUser(100L, "custA@example.com", RoleType.ROLE_CUSTOMER);
        Booking booking = Booking.builder().customer(customerA).totalAmount(BigDecimal.valueOf(1000.0)).build();
        booking.setId(50L);

        Payment payment = Payment.builder()
                .booking(booking)
                .gatewayOrderId("order_real_gateway_123")
                .gateway(PaymentGateway.RAZORPAY)
                .status(PaymentStatus.PENDING)
                .build();

        when(bookingRepo.findById(50L)).thenReturn(Optional.of(booking));
        when(paymentRepo.findByGatewayOrderId("order_real_gateway_123")).thenReturn(Optional.of(payment));

        // Client attempts to pass mockApproval=true on a real order with no signature
        PaymentVerifyRequest forgedReq = PaymentVerifyRequest.builder()
                .bookingId(50L)
                .orderId("order_real_gateway_123")
                .paymentId("pay_forged")
                .mockApproval(true)
                .build();

        boolean result = paymentService.verifyPayment(forgedReq, 100L, false);
        assertFalse(result, "Forged client mockApproval must be rejected when valid signature is absent");
    }

    @Test
    @DisplayName("Unsigned Razorpay webhook payload is rejected in production")
    void unsignedWebhookRejectedInProduction() {
        RazorpayClient razorpayClient = new RazorpayClient();
        ReflectionTestUtils.setField(razorpayClient, "allowMock", false);

        PaymentService paymentService = new PaymentService(bookingRepo, paymentRepo, vehicleRepo, driverRepo, razorpayClient, null);

        String payload = "{\"event\":\"payment.captured\"}";
        boolean handled = paymentService.handleWebhook(payload, null);
        assertFalse(handled, "Unsigned webhook must be rejected when mock is disabled in production");
    }

    // ==========================================
    // NOTIFICATION AUTHORIZATION
    // ==========================================

    @Test
    @DisplayName("User A cannot mark User B's notification as read")
    void userCannotMarkAnotherUserNotificationAsRead() {
        NotificationService notificationService = new NotificationService(notificationRepo, mapper);

        User userB = createUser(200L, "userB@example.com", RoleType.ROLE_CUSTOMER);
        Notification n = Notification.builder().user(userB).read(false).build();
        n.setId(88L);

        when(notificationRepo.findById(88L)).thenReturn(Optional.of(n));

        // User A (User 100) attempts to mark User B's notification as read
        assertThrows(AccessDeniedException.class, () ->
                notificationService.markAsRead(88L, 100L)
        );
    }

    // ==========================================
    // REVIEW SUBMISSION AUTHORIZATION
    // ==========================================

    @Test
    @DisplayName("Customer A cannot submit a review for Customer B's completed booking")
    void customerCannotReviewAnotherCustomerBooking() {
        ReviewService reviewService = new ReviewService(reviewRepo, bookingRepo, vehicleRepo, driverRepo, userRepo, null, mapper);

        User customerB = createUser(200L, "custB@example.com", RoleType.ROLE_CUSTOMER);
        Booking booking = Booking.builder()
                .customer(customerB)
                .bookingStatus(BookingStatus.COMPLETED)
                .vehicle(Vehicle.builder().build())
                .build();
        booking.setId(50L);

        when(bookingRepo.findById(50L)).thenReturn(Optional.of(booking));

        ReviewCreateRequest req = ReviewCreateRequest.builder()
                .bookingId(50L)
                .rating(BigDecimal.valueOf(5.0))
                .vehicleRating(BigDecimal.valueOf(5.0))
                .comment("Excellent ride!")
                .build();

        // Customer A (User 100) attempts to review Customer B's booking 50
        assertThrows(AccessDeniedException.class, () ->
                reviewService.submitReview(100L, req)
        );
    }

    // ==========================================
    // DRIVER ONLINE PRESENCE & HEARTBEAT
    // ==========================================

    @Test
    @DisplayName("Driver heartbeat refreshes online status and lastSeenAt")
    void driverHeartbeatRefreshesPresence() {
        DriverService driverService = new DriverService(driverRepo, mapper);
        User driverUser = createUser(100L, "driver@example.com", RoleType.ROLE_DRIVER);
        driverUser.setActive(true);

        Driver driver = Driver.builder()
                .licenseNumber("DL-100")
                .available(true)
                .online(false)
                .user(driverUser)
                .build();
        driver.setId(10L);

        when(driverRepo.findByUserId(100L)).thenReturn(Optional.of(driver));
        when(driverRepo.save(any(Driver.class))).thenAnswer(inv -> inv.getArgument(0));

        DriverDto dto = driverService.updateHeartbeat(100L);
        assertTrue(dto.isOnline(), "Driver must be marked online after heartbeat");
        assertNotNull(dto.getLastSeenAt(), "lastSeenAt must be populated");
    }

    @Test
    @DisplayName("Booking creation rejects offline driver with 409 conflict")
    void bookingCreationRejectsOfflineDriver() {
        BookingService bookingService = new BookingService(bookingRepo, vehicleRepo, driverRepo, userRepo, tripRepo, null, null, null, mapper);

        User customer = createUser(100L, "cust@example.com", RoleType.ROLE_CUSTOMER);
        Vehicle vehicle = Vehicle.builder().rentalPricePerDay(BigDecimal.valueOf(2000.0)).status(VehicleStatus.AVAILABLE).available(true).build();
        vehicle.setId(1L);

        User driverUser = createUser(200L, "driver@example.com", RoleType.ROLE_DRIVER);
        driverUser.setActive(true);

        Driver driver = Driver.builder()
                .licenseNumber("DL-200")
                .available(true)
                .online(false) // OFFLINE
                .dailyRate(BigDecimal.valueOf(800.0))
                .user(driverUser)
                .verification(DriverVerification.builder().status(VerificationStatus.APPROVED).build())
                .build();
        driver.setId(20L);

        when(userRepo.findById(100L)).thenReturn(Optional.of(customer));
        when(vehicleRepo.findById(1L)).thenReturn(Optional.of(vehicle));
        when(driverRepo.findById(20L)).thenReturn(Optional.of(driver));

        BookingCreateRequest req = BookingCreateRequest.builder()
                .vehicleId(1L)
                .driverId(20L)
                .startDateTime(java.time.LocalDateTime.now().plusDays(1))
                .endDateTime(java.time.LocalDateTime.now().plusDays(2))
                .pickupLocation("Bangalore")
                .dropLocation("Mysore")
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                bookingService.createBooking(100L, req)
        );
        assertTrue(ex.getMessage().contains("Selected driver is no longer available"));
    }

    @Test
    @DisplayName("Booking creation rejects driver with stale heartbeat (>120s)")
    void bookingCreationRejectsStaleHeartbeatDriver() {
        BookingService bookingService = new BookingService(bookingRepo, vehicleRepo, driverRepo, userRepo, tripRepo, null, null, null, mapper);

        User customer = createUser(100L, "cust@example.com", RoleType.ROLE_CUSTOMER);
        Vehicle vehicle = Vehicle.builder().rentalPricePerDay(BigDecimal.valueOf(2000.0)).status(VehicleStatus.AVAILABLE).available(true).build();
        vehicle.setId(1L);

        User driverUser = createUser(200L, "driver@example.com", RoleType.ROLE_DRIVER);
        driverUser.setActive(true);

        Driver driver = Driver.builder()
                .licenseNumber("DL-200")
                .available(true)
                .online(true)
                .lastSeenAt(java.time.LocalDateTime.now().minusSeconds(150)) // Stale (> 120s)
                .dailyRate(BigDecimal.valueOf(800.0))
                .user(driverUser)
                .verification(DriverVerification.builder().status(VerificationStatus.APPROVED).build())
                .build();
        driver.setId(20L);

        when(userRepo.findById(100L)).thenReturn(Optional.of(customer));
        when(vehicleRepo.findById(1L)).thenReturn(Optional.of(vehicle));
        when(driverRepo.findById(20L)).thenReturn(Optional.of(driver));

        BookingCreateRequest req = BookingCreateRequest.builder()
                .vehicleId(1L)
                .driverId(20L)
                .startDateTime(java.time.LocalDateTime.now().plusDays(1))
                .endDateTime(java.time.LocalDateTime.now().plusDays(2))
                .pickupLocation("Bangalore")
                .dropLocation("Mysore")
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                bookingService.createBooking(100L, req)
        );
        assertTrue(ex.getMessage().contains("Selected driver is no longer available"));
    }

    @Test
    @DisplayName("Admin can delete review and ratings are recalculated")
    void adminCanDeleteReview() {
        ReviewService reviewService = new ReviewService(reviewRepo, bookingRepo, vehicleRepo, driverRepo, userRepo, null, mapper);

        Vehicle vehicle = Vehicle.builder().averageRating(BigDecimal.valueOf(4.5)).totalRatings(2).build();
        vehicle.setId(1L);

        Review review = Review.builder()
                .vehicle(vehicle)
                .rating(BigDecimal.valueOf(4.0))
                .vehicleRating(BigDecimal.valueOf(4.0))
                .build();
        review.setId(99L);

        when(reviewRepo.findById(99L)).thenReturn(Optional.of(review));
        when(reviewRepo.findByVehicleIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        reviewService.deleteReview(99L);
        Mockito.verify(reviewRepo).delete(review);
        Mockito.verify(vehicleRepo).save(vehicle);
        assertEquals(0, vehicle.getTotalRatings());
    }
}
