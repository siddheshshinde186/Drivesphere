package com.drivesphere.service;

import com.drivesphere.controller.UserController;
import com.drivesphere.dto.*;
import com.drivesphere.entity.*;
import com.drivesphere.mapper.EntityDtoMapper;
import com.drivesphere.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CustomerProfileAndCheckoutTests {

    private UserRepository userRepository;
    private RoleRepository roleRepository;
    private BookingRepository bookingRepository;
    private PaymentRepository paymentRepository;
    private VehicleRepository vehicleRepository;
    private DriverRepository driverRepository;
    private com.drivesphere.integration.razorpay.RazorpayClient razorpayClient;
    private EntityDtoMapper mapper;

    private UserService userService;
    private AuthService authService;
    private PaymentService paymentService;
    private UserController userController;

    private User testUser;
    private UserDto testUserDto;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        roleRepository = Mockito.mock(RoleRepository.class);
        bookingRepository = Mockito.mock(BookingRepository.class);
        paymentRepository = Mockito.mock(PaymentRepository.class);
        vehicleRepository = Mockito.mock(VehicleRepository.class);
        driverRepository = Mockito.mock(DriverRepository.class);
        razorpayClient = Mockito.mock(com.drivesphere.integration.razorpay.RazorpayClient.class);
        authService = Mockito.mock(AuthService.class);
        mapper = new EntityDtoMapper();

        userService = new UserService(userRepository, roleRepository, bookingRepository, mapper);
        userController = new UserController(userService, authService);
        paymentService = new PaymentService(bookingRepository, paymentRepository, vehicleRepository, driverRepository, razorpayClient, null);

        testUser = User.builder()
                .email("priya.nair@example.com")
                .firstName("Priya")
                .lastName("Nair")
                .phoneNumber("+919876543210")
                .avatarUrl("https://example.com/avatar.jpg")
                .active(true)
                .verified(true)
                .emailVerified(true)
                .roles(Set.of(Role.builder().name(RoleType.ROLE_CUSTOMER).build()))
                .build();
        testUser.setId(101L);

        testUserDto = mapper.toUserDto(testUser);
    }

    @Test
    @DisplayName("1. Profile endpoint retrieves authenticated user derived from Security Context")
    void testGetMyProfile_derivedFromSecurityContext() {
        when(authService.getCurrentUser()).thenReturn(testUserDto);

        ResponseEntity<ApiResponse<UserDto>> response = userController.getMyProfile();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("Priya", response.getBody().getData().getFirstName());
        assertEquals("Nair", response.getBody().getData().getLastName());
        assertEquals("+919876543210", response.getBody().getData().getPhoneNumber());
        assertTrue(response.getBody().getData().isEmailVerified());
    }

    @Test
    @DisplayName("2. Profile update safely updates name, phone and avatar while preserving email and roles")
    void testUpdateMyProfile_safeUpdate() {
        when(authService.getCurrentUser()).thenReturn(testUserDto);
        when(userRepository.findById(101L)).thenReturn(Optional.of(testUser));
        when(userRepository.findByPhoneNumber("+919999888877")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserProfileUpdateRequest req = UserProfileUpdateRequest.builder()
                .firstName("Priya Ananya")
                .lastName("Verma")
                .phoneNumber("+919999888877")
                .avatarUrl("https://example.com/new-avatar.png")
                .build();

        ResponseEntity<ApiResponse<UserDto>> response = userController.updateMyProfile(req);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        UserDto updated = response.getBody().getData();
        assertEquals("Priya Ananya", updated.getFirstName());
        assertEquals("Verma", updated.getLastName());
        assertEquals("+919999888877", updated.getPhoneNumber());
        assertEquals("https://example.com/new-avatar.png", updated.getAvatarUrl());
        // Email remains strictly unchanged
        assertEquals("priya.nair@example.com", updated.getEmail());
    }

    @Test
    @DisplayName("3. Profile update rejects duplicate phone number belonging to another user")
    void testUpdateMyProfile_rejectsDuplicatePhone() {
        when(authService.getCurrentUser()).thenReturn(testUserDto);
        when(userRepository.findById(101L)).thenReturn(Optional.of(testUser));

        User conflictingUser = User.builder().phoneNumber("+919111122223").build();
        conflictingUser.setId(202L); // Different user ID
        when(userRepository.findByPhoneNumber("+919111122223")).thenReturn(Optional.of(conflictingUser));

        UserProfileUpdateRequest req = UserProfileUpdateRequest.builder()
                .firstName("Priya")
                .lastName("Nair")
                .phoneNumber("+919111122223")
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                userController.updateMyProfile(req)
        );
        assertTrue(ex.getMessage().contains("already associated with another account"));
    }

    @Test
    @DisplayName("4. Customer statistics accurately compute total bookings, active trips, and total spent")
    void testUserStats_accurateComputation() {
        when(authService.getCurrentUser()).thenReturn(testUserDto);

        Booking b1 = Booking.builder().bookingStatus(BookingStatus.ACTIVE).paymentStatus(PaymentStatus.PAID).totalAmount(BigDecimal.valueOf(4500.0)).build();
        Booking b2 = Booking.builder().bookingStatus(BookingStatus.COMPLETED).paymentStatus(PaymentStatus.PAID).totalAmount(BigDecimal.valueOf(3200.0)).build();
        Booking b3 = Booking.builder().bookingStatus(BookingStatus.CANCELLED).paymentStatus(PaymentStatus.FAILED).totalAmount(BigDecimal.valueOf(2000.0)).build();

        when(bookingRepository.findByCustomerIdOrderByCreatedAtDesc(101L)).thenReturn(List.of(b1, b2, b3));

        ResponseEntity<ApiResponse<UserStatsDto>> response = userController.getMyStats();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        UserStatsDto stats = response.getBody().getData();
        assertEquals(3, stats.getTotalBookings());
        assertEquals(1, stats.getActiveRentals());
        assertEquals(1, stats.getCompletedTrips());
        assertEquals(BigDecimal.valueOf(7700.0), stats.getTotalSpent());
    }

    @Test
    @DisplayName("5. Password change forwards strictly to authenticated AuthService")
    void testChangePassword_secureDelegation() {
        when(authService.getCurrentUser()).thenReturn(testUserDto);
        doNothing().when(authService).changePassword(eq(101L), any(ChangePasswordRequest.class));

        ChangePasswordRequest req = ChangePasswordRequest.builder()
                .currentPassword("OldPass@123")
                .newPassword("NewSecure@456")
                .build();

        ResponseEntity<ApiResponse<Void>> response = userController.changePassword(req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(authService, times(1)).changePassword(eq(101L), eq(req));
    }

    @Test
    @DisplayName("6. Payment order creation reuses recent pending order on retry instead of creating duplicates")
    void testPaymentOrder_reusesExistingPendingOrderOnRetry() {
        Booking booking = Booking.builder()
                .customer(testUser)
                .bookingReference("DS-20261031-TEST1")
                .bookingStatus(BookingStatus.HELD)
                .paymentStatus(PaymentStatus.PENDING)
                .totalAmount(BigDecimal.valueOf(3500.0))
                .build();
        booking.setId(555L);

        Payment recentPending = Payment.builder()
                .booking(booking)
                .amount(BigDecimal.valueOf(3500.0))
                .currency("INR")
                .gateway(PaymentGateway.RAZORPAY)
                .gatewayOrderId("order_existing_12345")
                .status(PaymentStatus.PENDING)
                .build();
        recentPending.setCreatedAt(Instant.now().minusSeconds(60)); // 1 min ago

        when(bookingRepository.findById(555L)).thenReturn(Optional.of(booking));
        when(paymentRepository.findFirstByBookingIdAndStatusOrderByCreatedAtDesc(555L, PaymentStatus.PENDING))
                .thenReturn(Optional.of(recentPending));
        when(razorpayClient.getKeyId()).thenReturn("rzp_test_key_abc");

        PaymentOrderResponse response = paymentService.createPaymentOrder(555L, 101L, false);

        assertNotNull(response);
        assertEquals("order_existing_12345", response.getOrderId());
        assertEquals(BigDecimal.valueOf(3500.0), response.getAmount());
        assertEquals("DS-20261031-TEST1", response.getBookingReference());
        // Verify razorpayClient.createOrder was NOT called again because order was reused!
        verify(razorpayClient, never()).createOrder(any(), any(), any());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("7. Travel preferences persist in database and map to UserDto")
    void testTravelPreferences_persistenceAndMapping() {
        when(authService.getCurrentUser()).thenReturn(testUserDto);
        when(userRepository.findById(101L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserProfileUpdateRequest req = UserProfileUpdateRequest.builder()
                .preferredVehicleType("ELECTRIC")
                .driverPreference("WITH_DRIVER")
                .build();

        ResponseEntity<ApiResponse<UserDto>> response = userController.updateMyProfile(req);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        UserDto updated = response.getBody().getData();
        assertEquals("ELECTRIC", updated.getPreferredVehicleType());
        assertEquals("WITH_DRIVER", updated.getDriverPreference());
        assertEquals("ELECTRIC", testUser.getPreferredVehicleType());
        assertEquals("WITH_DRIVER", testUser.getDriverPreference());
    }

    @Test
    @DisplayName("8. User stats computation handles null totalAmount and missing payment status without NPE")
    void testUserStats_nullSafeComputation() {
        when(authService.getCurrentUser()).thenReturn(testUserDto);

        Booking bValid = Booking.builder().bookingStatus(BookingStatus.ACTIVE).paymentStatus(PaymentStatus.PAID).totalAmount(BigDecimal.valueOf(5000.0)).build();
        Booking bNullAmount = Booking.builder().bookingStatus(BookingStatus.ACTIVE).paymentStatus(PaymentStatus.PAID).totalAmount(null).build();
        Booking bNullPaymentStatus = Booking.builder().bookingStatus(BookingStatus.ACTIVE).paymentStatus(null).totalAmount(BigDecimal.valueOf(1000.0)).build();

        when(bookingRepository.findByCustomerIdOrderByCreatedAtDesc(101L)).thenReturn(List.of(bValid, bNullAmount, bNullPaymentStatus));

        ResponseEntity<ApiResponse<UserStatsDto>> response = userController.getMyStats();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        UserStatsDto stats = response.getBody().getData();
        assertEquals(3, stats.getTotalBookings());
        assertEquals(3, stats.getActiveRentals());
        assertEquals(BigDecimal.valueOf(5000.0), stats.getTotalSpent());
    }

    @Test
    @DisplayName("9. Avatar upload rejects non-image formats and updates avatar URL for valid images")
    void testAvatarUpload_validationAndPersistence() {
        DocumentStorageService docService = Mockito.mock(DocumentStorageService.class);
        UserController controllerWithStorage = new UserController(userService, authService, docService);

        when(authService.getCurrentUser()).thenReturn(testUserDto);
        when(userRepository.findById(101L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        // Invalid PDF file rejection
        org.springframework.mock.web.MockMultipartFile pdfFile = new org.springframework.mock.web.MockMultipartFile(
                "file", "doc.pdf", "application/pdf", "some pdf content".getBytes()
        );
        ResponseEntity<ApiResponse<UserDto>> pdfResponse = controllerWithStorage.uploadAvatar(pdfFile);
        assertEquals(HttpStatus.BAD_REQUEST, pdfResponse.getStatusCode());
        assertTrue(pdfResponse.getBody().getMessage().contains("Only JPEG, PNG, and WEBP"));

        // Valid PNG file upload
        org.springframework.mock.web.MockMultipartFile pngFile = new org.springframework.mock.web.MockMultipartFile(
                "file", "avatar.png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G'}
        );
        StoredDocumentDto stored = StoredDocumentDto.builder()
                .fileUrl("https://example.com/uploaded-avatar.png")
                .filename("avatar_stored.png")
                .build();
        when(docService.storeFile(eq(pngFile), eq(101L))).thenReturn(stored);

        ResponseEntity<ApiResponse<UserDto>> successResponse = controllerWithStorage.uploadAvatar(pngFile);
        assertEquals(HttpStatus.OK, successResponse.getStatusCode());
        assertEquals("https://example.com/uploaded-avatar.png", successResponse.getBody().getData().getAvatarUrl());
        assertEquals("https://example.com/uploaded-avatar.png", testUser.getAvatarUrl());
    }

    @Test
    @DisplayName("10. Maintenance scheduling for future dates does not prematurely lock vehicle")
    void testScheduleMaintenance_noPrematureLocking() {
        MaintenanceRepository maintenanceRepository = Mockito.mock(MaintenanceRepository.class);
        MaintenanceService maintenanceService = new MaintenanceService(maintenanceRepository, vehicleRepository, mapper);

        Vehicle vehicle = Vehicle.builder()
                .brand("Tata")
                .model("Nexon EV")
                .status(VehicleStatus.AVAILABLE)
                .available(true)
                .currentOdometerKm(BigDecimal.valueOf(15000))
                .build();
        vehicle.setId(99L);

        when(vehicleRepository.findById(99L)).thenReturn(Optional.of(vehicle));
        when(maintenanceRepository.save(any(Maintenance.class))).thenAnswer(inv -> inv.getArgument(0));

        // Schedule maintenance 14 days in the future
        MaintenanceCreateRequest futureReq = MaintenanceCreateRequest.builder()
                .vehicleId(99L)
                .serviceType(ServiceType.INSPECTION)
                .serviceDate(java.time.LocalDate.now().plusDays(14))
                .description("Routine 20k checkup")
                .cost(BigDecimal.valueOf(2500))
                .build();

        MaintenanceDto result = maintenanceService.scheduleMaintenance(futureReq);

        assertNotNull(result);
        assertEquals(MaintenanceStatus.SCHEDULED, result.getStatus());
        // CRITICAL CHECK: Vehicle remains AVAILABLE and was NOT locked prematurely!
        assertEquals(VehicleStatus.AVAILABLE, vehicle.getStatus());
        assertTrue(vehicle.isAvailable());
    }

    @Test
    @DisplayName("11. Odometer update enforces non-decreasing validation and updates current reading")
    void testUpdateVehicleOdometer_nonDecreasingValidation() {
        MaintenanceRepository maintenanceRepository = Mockito.mock(MaintenanceRepository.class);
        MaintenanceService maintenanceService = new MaintenanceService(maintenanceRepository, vehicleRepository, mapper);

        Vehicle vehicle = Vehicle.builder()
                .brand("Hyundai")
                .model("Creta")
                .status(VehicleStatus.AVAILABLE)
                .available(true)
                .currentOdometerKm(BigDecimal.valueOf(20000))
                .lastServiceOdometerKm(BigDecimal.valueOf(15000))
                .build();
        vehicle.setId(77L);

        when(vehicleRepository.findById(77L)).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));

        // Attempting to lower the odometer reading must fail
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                maintenanceService.updateVehicleOdometer(77L, BigDecimal.valueOf(19500))
        );
        assertTrue(ex.getMessage().contains("cannot be less than current odometer reading"));

        // Valid forward update succeeds
        VehicleDto updated = maintenanceService.updateVehicleOdometer(77L, BigDecimal.valueOf(22500));
        assertNotNull(updated);
        assertEquals(BigDecimal.valueOf(22500), updated.getCurrentOdometerKm());
    }
}

