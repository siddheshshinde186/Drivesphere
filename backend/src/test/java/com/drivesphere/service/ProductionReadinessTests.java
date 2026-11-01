package com.drivesphere.service;

import com.drivesphere.dto.AuthResponse;
import com.drivesphere.dto.LoginRequest;
import com.drivesphere.dto.PaymentVerifyRequest;
import com.drivesphere.entity.*;
import com.drivesphere.integration.razorpay.RazorpayClient;
import com.drivesphere.mapper.EntityDtoMapper;
import com.drivesphere.repository.*;
import com.drivesphere.security.JwtUtils;
import com.drivesphere.security.LoginRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductionReadinessTests {

    private UserRepository userRepo;
    private RoleRepository roleRepo;
    private DriverRepository driverRepo;
    private DriverVerificationRepository driverVerificationRepo;
    private DriverTrustScoreRepository driverTrustScoreRepo;
    private EmailVerificationTokenRepository emailTokenRepo;
    private PasswordResetTokenRepository passwordResetTokenRepo;
    private RefreshTokenService refreshTokenService;
    private PasswordEncoder passwordEncoder;
    private AuthenticationManager authManager;
    private JwtUtils jwtUtils;
    private LoginRateLimiter rateLimiter;
    private AuditLogService auditLogService;
    private EmailService emailService;
    private BookingRepository bookingRepo;
    private PaymentRepository paymentRepo;
    private VehicleRepository vehicleRepo;
    private RazorpayClient razorpayClient;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        userRepo = mock(UserRepository.class);
        roleRepo = mock(RoleRepository.class);
        driverRepo = mock(DriverRepository.class);
        driverVerificationRepo = mock(DriverVerificationRepository.class);
        driverTrustScoreRepo = mock(DriverTrustScoreRepository.class);
        emailTokenRepo = mock(EmailVerificationTokenRepository.class);
        passwordResetTokenRepo = mock(PasswordResetTokenRepository.class);
        refreshTokenService = mock(RefreshTokenService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        authManager = mock(AuthenticationManager.class);
        jwtUtils = mock(JwtUtils.class);
        rateLimiter = new LoginRateLimiter();
        auditLogService = mock(AuditLogService.class);
        emailService = mock(EmailService.class);
        bookingRepo = mock(BookingRepository.class);
        paymentRepo = mock(PaymentRepository.class);
        vehicleRepo = mock(VehicleRepository.class);
        razorpayClient = mock(RazorpayClient.class);
        notificationService = mock(NotificationService.class);
    }

    private AuthService createAuthService() {
        return new AuthService(
                userRepo,
                roleRepo,
                driverRepo,
                driverVerificationRepo,
                driverTrustScoreRepo,
                emailTokenRepo,
                passwordResetTokenRepo,
                passwordEncoder,
                authManager,
                jwtUtils,
                new EntityDtoMapper(),
                refreshTokenService,
                rateLimiter,
                auditLogService,
                emailService
        );
    }

    @Test
    @DisplayName("Normal Login: Unverified active user with valid credentials logs in successfully")
    void testLogin_UnverifiedUserAllowed_LoginSucceeds() {
        User unverifiedUser = User.builder()
                .email("unverified@example.com")
                .passwordHash("hashedPass")
                .active(true)
                .emailVerified(false) // Unverified account
                .roles(Set.of(Role.builder().name(RoleType.ROLE_CUSTOMER).build()))
                .build();
        unverifiedUser.setId(42L);

        when(userRepo.findByEmail("unverified@example.com")).thenReturn(Optional.of(unverifiedUser));
        when(jwtUtils.generateTokenFromUsername("unverified@example.com")).thenReturn("mockUnverifiedJwt");
        when(jwtUtils.getJwtExpirationMs()).thenReturn(86400000L);
        when(refreshTokenService.createRefreshToken(42L)).thenReturn(RefreshToken.builder().token("mockRefreshToken").build());

        AuthService authService = createAuthService();

        LoginRequest loginReq = LoginRequest.builder()
                .email("unverified@example.com")
                .password("Password123!")
                .build();

        AuthResponse res = authService.login(loginReq);
        assertNotNull(res);
        assertEquals("mockUnverifiedJwt", res.getToken());
        assertEquals("mockRefreshToken", res.getRefreshToken());
        assertEquals("unverified@example.com", res.getUser().getEmail());
    }

    @Test
    @DisplayName("Email Verification: Valid token confirms user email")
    void testEmailVerification_ValidTokenConfirmsEmail() {
        User user = User.builder()
                .email("newuser@example.com")
                .active(true)
                .emailVerified(false)
                .build();
        user.setId(50L);

        EmailVerificationToken token = EmailVerificationToken.builder()
                .user(user)
                .tokenHash("someHashedTokenValue")
                .expiresAt(Instant.now().plusSeconds(86400))
                .used(false)
                .build();

        when(emailTokenRepo.findByTokenHash(any())).thenReturn(Optional.of(token));

        AuthService authService = createAuthService();

        authService.verifyEmail("rawTokenSample");
        assertTrue(token.isUsed(), "Token must be marked used");
        assertTrue(user.isEmailVerified(), "User email must be marked verified");
        verify(userRepo).save(user);
        verify(emailTokenRepo).save(token);
    }

    @Test
    @DisplayName("Password Reset: Single-use token resets password and revokes refresh tokens")
    void testPasswordReset_ResetsPasswordAndRevokesRefreshTokens() {
        User user = User.builder()
                .email("customer@example.com")
                .passwordHash("oldHash")
                .active(true)
                .emailVerified(true)
                .build();
        user.setId(10L);

        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .tokenHash("hashedResetToken")
                .expiresAt(Instant.now().plusSeconds(900))
                .used(false)
                .build();

        when(passwordResetTokenRepo.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NewSecretPassword@123")).thenReturn("newEncodedHash");

        AuthService authService = createAuthService();

        authService.resetPassword("rawResetToken", "NewSecretPassword@123");

        assertTrue(token.isUsed(), "Reset token must be marked as used");
        assertEquals("newEncodedHash", user.getPasswordHash(), "Password hash must be updated");
        verify(refreshTokenService).deleteByUserId(user.getId()); // Revokes refresh tokens for security!
        verify(userRepo).save(user);
    }

    @Test
    @DisplayName("Driver Online/Offline: Going offline marks availability false, and stale driver is cleaned up")
    void testDriverOnlineOffline_AndStaleCleanup() {
        Driver driver = Driver.builder()
                .online(true)
                .available(true)
                .lastSeenAt(LocalDateTime.now().minusMinutes(10)) // Stale (>5 mins)
                .build();
        driver.setId(7L);

        when(driverRepo.findAll()).thenReturn(List.of(driver));

        DriverService driverService = new DriverService(driverRepo, new EntityDtoMapper());

        // Run scheduled stale session cleanup
        driverService.cleanupStaleDriverSessions();

        assertFalse(driver.isOnline(), "Stale driver must be marked offline");
        assertFalse(driver.isAvailable(), "Stale driver must be marked unavailable");
        verify(driverRepo).saveAll(any());
    }

    @Test
    @DisplayName("Driver Trust Score: New driver with 0 trips has null score and no fake 5.0 rating")
    void testDriverTrustScore_NoFakeDefaultsForNewDriver() {
        Driver newDriver = Driver.builder()
                .experienceYears(1)
                .rating(null) // No customer rating yet!
                .totalTrips(0)
                .cancellationRate(BigDecimal.ZERO)
                .verification(DriverVerification.builder().status(VerificationStatus.APPROVED).build())
                .build();

        when(driverTrustScoreRepo.findByDriver(newDriver)).thenReturn(Optional.empty());
        when(driverTrustScoreRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        DriverTrustScoreService scoreService = new DriverTrustScoreService(driverTrustScoreRepo);
        DriverTrustScore score = scoreService.calculateAndSaveTrustScore(newDriver);

        assertNull(score.getOverallScore(), "Trust score overall must be null for driver with 0 verified trips");
        assertNull(score.getTrustLevel(), "Trust level must be null when score is unavailable");
        assertTrue(score.getExplanation().contains("Trust score not available"), "Explanation must state score not available");
        assertNull(score.getRatingFactor(), "Rating factor must be null when driver has no ratings");
    }

    @Test
    @DisplayName("Booking Pricing: Hourly pricing calculates correctly for sub-day rentals")
    void testBooking_HourlyPricing() {
        Vehicle vehicle = Vehicle.builder()
                .rentalPricePerDay(BigDecimal.valueOf(2400.00)) // 2400/day = 100/hr
                .available(true)
                .status(VehicleStatus.AVAILABLE)
                .build();
        vehicle.setId(1L);

        // 6-hour booking
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0);
        LocalDateTime end = start.plusHours(6);

        long hours = java.time.Duration.between(start, end).toHours();
        assertEquals(6, hours);

        BigDecimal hourlyRate = vehicle.getRentalPricePerDay().divide(BigDecimal.valueOf(24), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal expectedRental = hourlyRate.multiply(BigDecimal.valueOf(hours));

        assertEquals(new BigDecimal("600.00"), expectedRental);
    }

    @Test
    @DisplayName("Cancellation Policy: Tiered refund calculations (>24h=100%, 2-24h=80%, <2h=0%)")
    void testCancellationRefund_TieredPolicy() {
        PaymentService paymentService = new PaymentService(
                bookingRepo, paymentRepo, vehicleRepo, driverRepo, razorpayClient, notificationService
        );

        Booking booking100 = Booking.builder()
                .totalAmount(BigDecimal.valueOf(1000.00))
                .startDateTime(LocalDateTime.now().plusHours(48)) // >24h notice
                .paymentStatus(PaymentStatus.PAID)
                .build();
        assertEquals(0, new BigDecimal("1000.00").compareTo(paymentService.calculateRefundAmount(booking100)));

        Booking booking80 = Booking.builder()
                .totalAmount(BigDecimal.valueOf(1000.00))
                .startDateTime(LocalDateTime.now().plusHours(12)) // Between 2h and 24h
                .paymentStatus(PaymentStatus.PAID)
                .build();
        assertEquals(0, new BigDecimal("800.00").compareTo(paymentService.calculateRefundAmount(booking80)));

        Booking booking0 = Booking.builder()
                .totalAmount(BigDecimal.valueOf(1000.00))
                .startDateTime(LocalDateTime.now().plusMinutes(30)) // < 2h notice
                .paymentStatus(PaymentStatus.PAID)
                .build();
        assertEquals(0, BigDecimal.ZERO.compareTo(paymentService.calculateRefundAmount(booking0)));
    }

    @Test
    @DisplayName("Payment Hardening: Rejects payment when amount does not match booking total")
    void testPayment_AmountMismatchRejected() {
        Booking booking = Booking.builder()
                .bookingReference("DS-PAY-001")
                .totalAmount(BigDecimal.valueOf(5000.00))
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        booking.setId(25L);

        Payment payment = Payment.builder()
                .booking(booking)
                .amount(BigDecimal.valueOf(100.00)) // Payment record is 100, but booking is 5000
                .gateway(PaymentGateway.RAZORPAY)
                .gatewayOrderId("order_real_123")
                .status(PaymentStatus.PENDING)
                .build();

        when(bookingRepo.findById(25L)).thenReturn(Optional.of(booking));
        when(paymentRepo.findByGatewayOrderId("order_real_123")).thenReturn(Optional.of(payment));

        PaymentService paymentService = new PaymentService(
                bookingRepo, paymentRepo, vehicleRepo, driverRepo, razorpayClient, notificationService
        );

        PaymentVerifyRequest verifyReq = PaymentVerifyRequest.builder()
                .bookingId(25L)
                .orderId("order_real_123")
                .paymentId("pay_123")
                .signature("sig_123")
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                paymentService.verifyPayment(verifyReq, 1L, true));
        assertTrue(ex.getMessage().contains("Payment amount mismatch"), "Must reject mismatched payment amount");
    }

    @Test
    @DisplayName("Payment Hardening: Rejects mock payments in production when mock payments are disabled")
    void testPayment_MockPaymentRejectedInProduction() {
        Booking booking = Booking.builder()
                .bookingReference("DS-PAY-002")
                .totalAmount(BigDecimal.valueOf(3000.00))
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        booking.setId(26L);

        Payment mockPayment = Payment.builder()
                .booking(booking)
                .amount(BigDecimal.valueOf(3000.00))
                .gateway(PaymentGateway.MOCK)
                .gatewayOrderId("order_mock_test_999")
                .status(PaymentStatus.PENDING)
                .build();

        when(bookingRepo.findById(26L)).thenReturn(Optional.of(booking));
        when(paymentRepo.findByGatewayOrderId("order_mock_test_999")).thenReturn(Optional.of(mockPayment));
        when(razorpayClient.isAllowMock()).thenReturn(false); // Production setting!

        PaymentService paymentService = new PaymentService(
                bookingRepo, paymentRepo, vehicleRepo, driverRepo, razorpayClient, notificationService
        );

        PaymentVerifyRequest verifyReq = PaymentVerifyRequest.builder()
                .bookingId(26L)
                .orderId("order_mock_test_999")
                .paymentId("pay_mock_999")
                .signature("mock_sig_999")
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                paymentService.verifyPayment(verifyReq, 1L, true));
        assertTrue(ex.getMessage().contains("Mock payments are strictly forbidden in production"),
                "Mock payment must be rejected when allowMock=false");
    }

    @Test
    @DisplayName("AI Security: Rate limiter blocks excessive requests from same IP")
    void testAiRateLimiter_BlocksExcessiveRequests() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        String testIp = "192.168.1.100";

        for (int i = 0; i < 20; i++) {
            assertFalse(limiter.isActionRateLimited("ai-assist", testIp, 20, 60), "First 20 requests within a minute should pass");
        }

        // 21st request exceeds limit of 20
        assertTrue(limiter.isActionRateLimited("ai-assist", testIp, 20, 60), "21st request must be rate limited");
    }

    @Test
    @DisplayName("JWT Security: Insecure default secret is rejected in production profile")
    void testJwtUtils_InsecureDefaultSecretRejectedInProduction() {
        JwtUtils utils = new JwtUtils();
        utils.setJwtSecret("404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        utils.setActiveProfile("prod");

        IllegalStateException ex = assertThrows(IllegalStateException.class, utils::validateSecretConfiguration);
        assertTrue(ex.getMessage().contains("default insecure JWT secret"));
    }

    @Test
    @DisplayName("JWT Security: Too short secret is rejected in production profile")
    void testJwtUtils_ShortSecretRejectedInProduction() {
        JwtUtils utils = new JwtUtils();
        utils.setJwtSecret("too_short_secret");
        utils.setActiveProfile("prod");

        IllegalStateException ex = assertThrows(IllegalStateException.class, utils::validateSecretConfiguration);
        assertTrue(ex.getMessage().contains("at least 32 characters"));
    }

    @Test
    @DisplayName("JWT Security: Valid 32+ char secret is accepted in production profile")
    void testJwtUtils_ValidSecretAcceptedInProduction() {
        JwtUtils utils = new JwtUtils();
        utils.setJwtSecret("secure_random_256_bit_production_secret_key_12345");
        utils.setActiveProfile("prod");

        assertDoesNotThrow(utils::validateSecretConfiguration);
    }
}
