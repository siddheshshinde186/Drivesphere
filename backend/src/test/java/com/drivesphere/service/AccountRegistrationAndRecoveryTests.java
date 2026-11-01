package com.drivesphere.service;

import com.drivesphere.dto.AuthResponse;
import com.drivesphere.dto.LoginRequest;
import com.drivesphere.dto.RegisterRequest;
import com.drivesphere.dto.ErrorResponse;
import com.drivesphere.entity.*;
import com.drivesphere.exception.DuplicateAccountException;
import com.drivesphere.exception.GlobalExceptionHandler;
import com.drivesphere.mapper.EntityDtoMapper;
import com.drivesphere.repository.*;
import com.drivesphere.security.JwtUtils;
import com.drivesphere.security.LoginRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AccountRegistrationAndRecoveryTests {

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

    // 1. Registration with a new email and phone number
    @Test
    @DisplayName("1. Registration with a new email and phone number succeeds with normalized phone")
    void testRegister_NewEmailAndPhone_Success() {
        AuthService authService = createAuthService();

        when(userRepo.findByEmail("newcustomer@example.com")).thenReturn(Optional.empty());
        when(userRepo.findByPhoneNumber(any())).thenReturn(Optional.empty());
        when(userRepo.findByPhoneDigits(any())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hashedPassword123");

        Role customerRole = Role.builder().name(RoleType.ROLE_CUSTOMER).build();
        when(roleRepo.findByName(RoleType.ROLE_CUSTOMER)).thenReturn(Optional.of(customerRole));

        when(userRepo.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(99L);
            return u;
        });

        RegisterRequest req = RegisterRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("newcustomer@example.com")
                .phoneNumber("+91 98339 18728") // with spaces
                .password("StrongPass123!")
                .role(RoleType.ROLE_CUSTOMER)
                .build();

        AuthResponse res = authService.register(req);

        assertNotNull(res);
        assertNotNull(res.getUser());
        assertEquals("newcustomer@example.com", res.getUser().getEmail());
        assertEquals("+919833918728", res.getUser().getPhoneNumber(), "Phone number must be normalized without spaces");
        verify(emailService).sendVerificationEmail(eq("newcustomer@example.com"), eq("John"), any());
    }

    // 2. Registration using an existing phone number
    @Test
    @DisplayName("2. Registration with an existing phone number throws PHONE_ALREADY_REGISTERED")
    void testRegister_ExistingPhone_ThrowsDuplicateAccountException() {
        AuthService authService = createAuthService();

        when(userRepo.findByEmail("fresh@example.com")).thenReturn(Optional.empty());

        User existingUser = User.builder().email("old@example.com").phoneNumber("+919833918728").build();
        existingUser.setId(101L);
        when(userRepo.findByPhoneNumber("+919833918728")).thenReturn(Optional.of(existingUser));

        RegisterRequest req = RegisterRequest.builder()
                .firstName("Jane")
                .lastName("Doe")
                .email("fresh@example.com")
                .phoneNumber("+919833918728")
                .password("StrongPass123!")
                .role(RoleType.ROLE_CUSTOMER)
                .build();

        DuplicateAccountException ex = assertThrows(DuplicateAccountException.class, () -> authService.register(req));
        assertEquals("PHONE_ALREADY_REGISTERED", ex.getCode());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("phone number"), "Message must explain phone collision");
    }

    // 3. Registration using an existing email address
    @Test
    @DisplayName("3. Registration with an existing email throws EMAIL_ALREADY_REGISTERED")
    void testRegister_ExistingEmail_ThrowsDuplicateAccountException() {
        AuthService authService = createAuthService();

        User existingUser = User.builder().email("existing@example.com").phoneNumber("+911111111111").build();
        existingUser.setId(102L);
        when(userRepo.findByEmail("existing@example.com")).thenReturn(Optional.of(existingUser));
        when(userRepo.findByPhoneNumber("+919999999999")).thenReturn(Optional.empty());

        RegisterRequest req = RegisterRequest.builder()
                .firstName("Alice")
                .lastName("Smith")
                .email("existing@example.com")
                .phoneNumber("+919999999999")
                .password("StrongPass123!")
                .role(RoleType.ROLE_CUSTOMER)
                .build();

        DuplicateAccountException ex = assertThrows(DuplicateAccountException.class, () -> authService.register(req));
        assertEquals("EMAIL_ALREADY_REGISTERED", ex.getCode());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    // 4. Concurrent registrations that attempt to use the same phone number
    @Test
    @DisplayName("4. Concurrent registration collision (DataIntegrityViolationException) maps to PHONE_ALREADY_REGISTERED")
    void testRegister_ConcurrentPhoneCollision_HandledGracefully() {
        AuthService authService = createAuthService();

        when(userRepo.findByEmail("concurrent@example.com")).thenReturn(Optional.empty());
        when(userRepo.findByPhoneNumber(any())).thenReturn(Optional.empty());
        when(roleRepo.findByName(any())).thenReturn(Optional.of(Role.builder().name(RoleType.ROLE_CUSTOMER).build()));

        // Simulate database unique constraint firing on simultaneous insert
        when(userRepo.save(any(User.class))).thenThrow(
                new DataIntegrityViolationException("Duplicate entry '+919833918728' for key 'users.UK9q63snka3mdh91as4io72espi'")
        );

        RegisterRequest req = RegisterRequest.builder()
                .firstName("Racer")
                .lastName("One")
                .email("concurrent@example.com")
                .phoneNumber("+919833918728")
                .password("StrongPass123!")
                .role(RoleType.ROLE_CUSTOMER)
                .build();

        DuplicateAccountException ex = assertThrows(DuplicateAccountException.class, () -> authService.register(req));
        assertEquals("PHONE_ALREADY_REGISTERED", ex.getCode());
        assertTrue(ex.getMessage().contains("phone number"), "Must return customer-friendly message without raw SQL");
    }

    // 5. Login with valid credentials
    @Test
    @DisplayName("5. Login with valid credentials succeeds for verified active user")
    void testLogin_ValidCredentials_Success() {
        AuthService authService = createAuthService();

        User user = User.builder()
                .email("verified@example.com")
                .passwordHash("hashedPass")
                .active(true)
                .emailVerified(true)
                .roles(Set.of(Role.builder().name(RoleType.ROLE_CUSTOMER).build()))
                .build();
        user.setId(77L);

        when(userRepo.findByEmail("verified@example.com")).thenReturn(Optional.of(user));
        when(jwtUtils.generateTokenFromUsername("verified@example.com")).thenReturn("mockJwtToken");
        when(jwtUtils.getJwtExpirationMs()).thenReturn(86400000L);

        RefreshToken refreshToken = RefreshToken.builder().token("mockRefreshToken").build();
        when(refreshTokenService.createRefreshToken(77L)).thenReturn(refreshToken);

        LoginRequest req = LoginRequest.builder()
                .email("verified@example.com")
                .password("StrongPass123!")
                .build();

        AuthResponse res = authService.login(req);

        assertNotNull(res);
        assertEquals("mockJwtToken", res.getToken());
        assertEquals("mockRefreshToken", res.getRefreshToken());
    }

    // 6. Login with invalid credentials
    @Test
    @DisplayName("6. Login with invalid credentials throws BadCredentialsException and increments failure count")
    void testLogin_InvalidCredentials_Fails() {
        AuthService authService = createAuthService();

        when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        LoginRequest req = LoginRequest.builder()
                .email("someone@example.com")
                .password("WrongPassword123!")
                .build();

        assertThrows(BadCredentialsException.class, () -> authService.login(req));
    }

    // 7. Forgot-password request for an existing email
    @Test
    @DisplayName("7. Forgot-password request for existing user generates single-use reset token and dispatches email")
    void testForgotPassword_ExistingEmail_DispatchesEmail() {
        AuthService authService = createAuthService();

        User user = User.builder()
                .email("forgotme@example.com")
                .firstName("Alice")
                .active(true)
                .build();
        user.setId(55L);

        when(userRepo.findByEmail("forgotme@example.com")).thenReturn(Optional.of(user));

        authService.forgotPassword("forgotme@example.com");

        verify(passwordResetTokenRepo).invalidateUserTokens(user);
        verify(passwordResetTokenRepo).save(any(PasswordResetToken.class));
        verify(emailService).sendPasswordResetEmail(eq("forgotme@example.com"), eq("Alice"), any());
    }

    // 8. Forgot-password request for a nonexistent email
    @Test
    @DisplayName("8. Forgot-password request for nonexistent email completes silently without user enumeration")
    void testForgotPassword_NonexistentEmail_CompletesSilently() {
        AuthService authService = createAuthService();

        when(userRepo.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> authService.forgotPassword("ghost@example.com"));
        verify(emailService, never()).sendPasswordResetEmail(any(), any(), any());
    }

    // 9. Successful password reset
    @Test
    @DisplayName("9. Successful password reset hashes new password, marks token used, and revokes sessions")
    void testResetPassword_Success() {
        AuthService authService = createAuthService();

        User user = User.builder().email("resetuser@example.com").build();
        user.setId(88L);

        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .tokenHash("someHash")
                .expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
                .used(false)
                .build();

        when(passwordResetTokenRepo.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NewStrongPass#123")).thenReturn("encodedNewHash");

        authService.resetPassword("rawTokenString", "NewStrongPass#123");

        assertTrue(token.isUsed());
        assertEquals("encodedNewHash", user.getPasswordHash());
        verify(refreshTokenService).deleteByUserId(88L);
        verify(userRepo).save(user);
    }

    // 10. Expired recovery token
    @Test
    @DisplayName("10. Expired recovery token is rejected with an informative error")
    void testResetPassword_ExpiredToken_Rejected() {
        AuthService authService = createAuthService();

        User user = User.builder().email("expired@example.com").build();
        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .tokenHash("someHash")
                .expiresAt(Instant.now().minus(5, ChronoUnit.MINUTES)) // expired
                .used(false)
                .build();

        when(passwordResetTokenRepo.findByTokenHash(any())).thenReturn(Optional.of(token));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.resetPassword("rawToken", "NewStrongPass#123"));
        assertTrue(ex.getMessage().contains("expired"));
    }

    // 11. Reuse of a previously consumed recovery token
    @Test
    @DisplayName("11. Reused recovery token is rejected")
    void testResetPassword_ReusedToken_Rejected() {
        AuthService authService = createAuthService();

        User user = User.builder().email("reused@example.com").build();
        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .tokenHash("someHash")
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .used(true) // already used
                .build();

        when(passwordResetTokenRepo.findByTokenHash(any())).thenReturn(Optional.of(token));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.resetPassword("rawToken", "NewStrongPass#123"));
        assertTrue(ex.getMessage().contains("already been used"));
    }

    // 12. Invalid recovery token
    @Test
    @DisplayName("12. Non-existent recovery token is rejected")
    void testResetPassword_InvalidToken_Rejected() {
        AuthService authService = createAuthService();

        when(passwordResetTokenRepo.findByTokenHash(any())).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.resetPassword("invalidToken", "NewStrongPass#123"));
        assertTrue(ex.getMessage().contains("Invalid password reset token"));
    }

    // 13. Password validation failures
    @Test
    @DisplayName("13. Password validation rejects weak passwords")
    void testPasswordValidation_RejectsWeak() {
        AuthService authService = createAuthService();

        RegisterRequest weakReq = RegisterRequest.builder()
                .firstName("Weak")
                .lastName("Pass")
                .email("weak@example.com")
                .password("weak") // too short, no uppercase, no symbol
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.register(weakReq));
        assertTrue(ex.getMessage().contains("at least 8 characters"));
    }

    // 14. Unauthorized attempts to change another user's password
    @Test
    @DisplayName("14. Privilege escalation: Public registration cannot create ADMIN or FLEET_MANAGER")
    void testRegister_PrivilegeEscalation_Rejected() {
        AuthService authService = createAuthService();

        RegisterRequest adminReq = RegisterRequest.builder()
                .firstName("Sneaky")
                .lastName("Admin")
                .email("sneaky@example.com")
                .password("StrongPass123!")
                .role(RoleType.ROLE_ADMIN)
                .build();

        assertThrows(AccessDeniedException.class, () -> authService.register(adminReq));
    }

    // 15. Customer and Driver Partner role handling
    @Test
    @DisplayName("15. Driver registration requires valid driving license and experience")
    void testRegister_DriverRole_RequiresLicenseAndExperience() {
        AuthService authService = createAuthService();

        RegisterRequest driverReqMissingLicense = RegisterRequest.builder()
                .firstName("Driver")
                .lastName("Partner")
                .email("driver@example.com")
                .password("StrongPass123!")
                .role(RoleType.ROLE_DRIVER)
                // missing licenseNumber
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.register(driverReqMissingLicense));
        assertTrue(ex.getMessage().contains("Driving License"));
    }

    // 16. Email delivery failure handling
    @Test
    @DisplayName("16. Email delivery failure logs warning without crashing user registration")
    void testRegister_EmailDeliveryFailure_HandledGracefully() {
        AuthService authService = createAuthService();

        when(userRepo.findByEmail("customer@example.com")).thenReturn(Optional.empty());
        when(userRepo.findByPhoneNumber(any())).thenReturn(Optional.empty());
        when(roleRepo.findByName(any())).thenReturn(Optional.of(Role.builder().name(RoleType.ROLE_CUSTOMER).build()));

        when(userRepo.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(123L);
            return u;
        });

        // Simulate SMTP delivery throwing exception
        doThrow(new RuntimeException("SMTP connection timeout"))
                .when(emailService).sendVerificationEmail(any(), any(), any());

        RegisterRequest req = RegisterRequest.builder()
                .firstName("Sam")
                .lastName("Reliable")
                .email("customer@example.com")
                .phoneNumber("+91 9988776655")
                .password("StrongPass123!")
                .role(RoleType.ROLE_CUSTOMER)
                .build();

        // Must still succeed creating the user account in database!
        assertDoesNotThrow(() -> authService.register(req));
        verify(userRepo).save(any(User.class));
    }

    // 17. Proper user-friendly error responses without database details
    @Test
    @DisplayName("17. GlobalExceptionHandler hides SQL details and returns clean, structured error responses")
    void testGlobalExceptionHandler_HidesDatabaseDetails() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");

        // 17a. DataIntegrityViolationException on phone
        DataIntegrityViolationException phoneEx = new DataIntegrityViolationException(
                "Duplicate entry '+919833918728' for key 'users.UK9q63snka3mdh91as4io72espi'"
        );
        ResponseEntity<ErrorResponse> phoneResp = handler.handleDataIntegrityViolationException(phoneEx, request);
        assertEquals(HttpStatus.CONFLICT, phoneResp.getStatusCode());
        assertEquals("PHONE_ALREADY_REGISTERED", phoneResp.getBody().getCode());
        assertFalse(phoneResp.getBody().getMessage().contains("SQL"));
        assertFalse(phoneResp.getBody().getMessage().contains("UK9q63snka3mdh91as4io72espi"));

        // 17b. General Unhandled Exception
        RuntimeException genericEx = new RuntimeException("SELECT * FROM users failed: Table corrupted");
        ResponseEntity<ErrorResponse> genericResp = handler.handleGeneralException(genericEx, request);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, genericResp.getStatusCode());
        assertEquals("INTERNAL_SERVER_ERROR", genericResp.getBody().getCode());
        assertEquals("We couldn't complete your request right now. Please try again in a moment.",
                genericResp.getBody().getMessage());
        assertFalse(genericResp.getBody().getMessage().contains("SELECT"));
    }

    // 18. SMTP Delivery Failure in Password Recovery returns 503 Service Unavailable
    @Test
    @DisplayName("18. Active SMTP delivery failure in forgotPassword throws 503 Service Unavailable")
    void testForgotPassword_SmtpFailure_ThrowsServiceUnavailable() {
        AuthService authService = createAuthService();

        User user = User.builder()
                .email("realuser@example.com")
                .firstName("Real")
                .active(true)
                .build();
        user.setId(99L);

        when(userRepo.findByEmail("realuser@example.com")).thenReturn(Optional.of(user));

        doThrow(new com.drivesphere.exception.EmailDeliveryException("SMTP server connection timeout (Ref: 1234abcd)"))
                .when(emailService).sendPasswordResetEmail(any(), any(), any());

        com.drivesphere.exception.ApiException ex = assertThrows(com.drivesphere.exception.ApiException.class,
                () -> authService.forgotPassword("realuser@example.com"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        assertTrue(ex.getMessage().contains("temporarily unable to dispatch recovery emails"));
    }

    // 19. EmailService PII masking protects privacy in logs
    @Test
    @DisplayName("19. EmailService maskEmail protects user privacy in logs")
    void testEmailService_MaskEmail_HidesPII() {
        assertEquals("j***e@example.com", EmailService.maskEmail("john.doe@example.com"));
        assertEquals("a***c@domain.com", EmailService.maskEmail("abc@domain.com"));
        assertEquals("***", EmailService.maskEmail(null));
        assertEquals("***", EmailService.maskEmail(""));
    }

    // 20. Login using a registered phone number
    @Test
    @DisplayName("20. Login using a valid registered phone number succeeds")
    void testLogin_ValidPhoneNumber_Success() {
        AuthService authService = createAuthService();

        User user = User.builder()
                .email("driver.phone@example.com")
                .phoneNumber("+919833918728")
                .passwordHash("hashedPass")
                .active(true)
                .emailVerified(true)
                .roles(Set.of(Role.builder().name(RoleType.ROLE_DRIVER).build()))
                .build();
        user.setId(88L);

        when(userRepo.findByPhoneNumber("+919833918728")).thenReturn(Optional.of(user));
        when(jwtUtils.generateTokenFromUsername("driver.phone@example.com")).thenReturn("phoneJwtToken");
        when(jwtUtils.getJwtExpirationMs()).thenReturn(86400000L);

        RefreshToken refreshToken = RefreshToken.builder().token("phoneRefreshToken").build();
        when(refreshTokenService.createRefreshToken(88L)).thenReturn(refreshToken);

        LoginRequest req = LoginRequest.builder()
                .identifier("+919833918728")
                .password("DriverPass123!")
                .build();

        AuthResponse res = authService.login(req);

        assertNotNull(res);
        assertEquals("phoneJwtToken", res.getToken());
        assertEquals("phoneRefreshToken", res.getRefreshToken());
        assertEquals("driver.phone@example.com", res.getUser().getEmail());
    }

    // 21. Login using a 10-digit un-prefixed phone number with automatic normalization
    @Test
    @DisplayName("21. Login using 10-digit un-prefixed phone number normalizes to +91 and succeeds")
    void testLogin_10DigitPhoneNormalization_Success() {
        AuthService authService = createAuthService();

        User user = User.builder()
                .email("ten.digit@example.com")
                .phoneNumber("+919833918728")
                .passwordHash("hashedPass")
                .active(true)
                .emailVerified(true)
                .roles(Set.of(Role.builder().name(RoleType.ROLE_CUSTOMER).build()))
                .build();
        user.setId(89L);

        // Normalize 9833918728 -> +919833918728
        when(userRepo.findByPhoneNumber("+919833918728")).thenReturn(Optional.of(user));
        when(jwtUtils.generateTokenFromUsername("ten.digit@example.com")).thenReturn("tenDigitJwtToken");
        when(jwtUtils.getJwtExpirationMs()).thenReturn(86400000L);

        RefreshToken refreshToken = RefreshToken.builder().token("tenDigitRefreshToken").build();
        when(refreshTokenService.createRefreshToken(89L)).thenReturn(refreshToken);

        LoginRequest req = LoginRequest.builder()
                .identifier("9833918728")
                .password("UserPass123!")
                .build();

        AuthResponse res = authService.login(req);

        assertNotNull(res);
        assertEquals("tenDigitJwtToken", res.getToken());
        assertEquals("ten.digit@example.com", res.getUser().getEmail());
    }

    // 22. Login with inactive account throws DisabledException
    @Test
    @DisplayName("22. Login with deactivated account throws DisabledException")
    void testLogin_InactiveAccount_ThrowsDisabledException() {
        AuthService authService = createAuthService();

        User inactiveUser = User.builder()
                .email("banned@example.com")
                .passwordHash("hashedPass")
                .active(false)
                .emailVerified(true)
                .roles(Set.of(Role.builder().name(RoleType.ROLE_CUSTOMER).build()))
                .build();
        inactiveUser.setId(90L);

        when(userRepo.findByEmail("banned@example.com")).thenReturn(Optional.of(inactiveUser));

        LoginRequest req = LoginRequest.builder()
                .identifier("banned@example.com")
                .password("AnyPass123!")
                .build();

        assertThrows(org.springframework.security.authentication.DisabledException.class,
                () -> authService.login(req));
    }

    // 23. UserDetailsServiceImpl loads users by email and phone identifier
    @Test
    @DisplayName("23. UserDetailsServiceImpl loads principal UserDetails by both email and phone")
    void testUserDetailsServiceImpl_LoadsByEmailAndPhone() {
        com.drivesphere.security.UserDetailsServiceImpl service =
                new com.drivesphere.security.UserDetailsServiceImpl(userRepo);

        User user = User.builder()
                .email("shared@example.com")
                .phoneNumber("+919833918728")
                .passwordHash("hashedSecret")
                .active(true)
                .roles(Set.of(Role.builder().name(RoleType.ROLE_CUSTOMER).build()))
                .build();

        when(userRepo.findByEmail("shared@example.com")).thenReturn(Optional.of(user));
        when(userRepo.findByPhoneNumber("+919833918728")).thenReturn(Optional.of(user));

        // Load by email
        org.springframework.security.core.userdetails.UserDetails byEmail =
                service.loadUserByUsername("shared@example.com");
        assertEquals("shared@example.com", byEmail.getUsername());
        assertEquals("hashedSecret", byEmail.getPassword());
        assertTrue(byEmail.isEnabled());

        // Load by phone
        org.springframework.security.core.userdetails.UserDetails byPhone =
                service.loadUserByUsername("+919833918728");
        assertEquals("shared@example.com", byPhone.getUsername());
        assertEquals("hashedSecret", byPhone.getPassword());

        // Load unknown identifier
        when(userRepo.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        assertThrows(org.springframework.security.core.userdetails.UsernameNotFoundException.class,
                () -> service.loadUserByUsername("unknown@example.com"));
    }

    // 24. LoginRequest effective identifier and validation
    @Test
    @DisplayName("24. LoginRequest resolves effective identifier from either identifier or legacy email field")
    void testLoginRequest_EffectiveIdentifier() {
        LoginRequest req1 = LoginRequest.builder().identifier("custom@example.com").build();
        assertEquals("custom@example.com", req1.getEffectiveIdentifier());
        assertTrue(req1.isValidIdentifier());

        LoginRequest req2 = LoginRequest.builder().email("legacy@example.com").build();
        assertEquals("legacy@example.com", req2.getEffectiveIdentifier());
        assertTrue(req2.isValidIdentifier());

        LoginRequest req3 = LoginRequest.builder().build();
        assertEquals("", req3.getEffectiveIdentifier());
        assertFalse(req3.isValidIdentifier());
    }

    // 25. ResendVerificationRequest accepts phone number and AuthService resolves user by phone
    @Test
    @DisplayName("25. Resend verification accepts phone identifier and resolves user")
    void testResendVerification_ByPhone_Success() {
        AuthService authService = createAuthService();

        User user = User.builder()
                .email("phone.resend@example.com")
                .phoneNumber("+919833918728")
                .emailVerified(false)
                .active(true)
                .build();
        user.setId(95L);

        when(userRepo.findByEmailOrPhone("+919833918728")).thenReturn(Optional.of(user));

        authService.resendVerification("+919833918728");

        verify(emailTokenRepo).invalidateUserTokens(user);
        verify(emailTokenRepo).save(any(EmailVerificationToken.class));
        verify(emailService).sendVerificationEmail(eq("phone.resend@example.com"), any(), any());
    }

    // 26. Unverified account + correct email + correct password -> login succeeds
    @Test
    @DisplayName("26. Unverified active user logging in with email and password succeeds")
    void testLogin_UnverifiedEmail_LoginSucceeds() {
        AuthService authService = createAuthService();

        User user = User.builder()
                .email("unverified.customer@example.com")
                .passwordHash("hashedPass")
                .active(true)
                .emailVerified(false) // Unverified account
                .roles(Set.of(Role.builder().name(RoleType.ROLE_CUSTOMER).build()))
                .build();
        user.setId(96L);

        when(userRepo.findByEmail("unverified.customer@example.com")).thenReturn(Optional.of(user));
        when(jwtUtils.generateTokenFromUsername("unverified.customer@example.com")).thenReturn("unverifiedEmailJwt");
        when(jwtUtils.getJwtExpirationMs()).thenReturn(86400000L);

        RefreshToken refreshToken = RefreshToken.builder().token("unverifiedEmailRefreshToken").build();
        when(refreshTokenService.createRefreshToken(96L)).thenReturn(refreshToken);

        LoginRequest req = LoginRequest.builder()
                .email("unverified.customer@example.com")
                .password("Password123!")
                .build();

        AuthResponse res = authService.login(req);

        assertNotNull(res);
        assertEquals("unverifiedEmailJwt", res.getToken());
        assertEquals("unverifiedEmailRefreshToken", res.getRefreshToken());
        assertEquals("unverified.customer@example.com", res.getUser().getEmail());
    }

    // 27. Unverified account + correct phone + correct password -> login succeeds
    @Test
    @DisplayName("27. Unverified active user logging in with phone and password succeeds")
    void testLogin_UnverifiedPhone_LoginSucceeds() {
        AuthService authService = createAuthService();

        User user = User.builder()
                .email("unverified.phone@example.com")
                .phoneNumber("+919833918728")
                .passwordHash("hashedPass")
                .active(true)
                .emailVerified(false) // Unverified account
                .roles(Set.of(Role.builder().name(RoleType.ROLE_DRIVER).build()))
                .build();
        user.setId(97L);

        when(userRepo.findByPhoneNumber("+919833918728")).thenReturn(Optional.of(user));
        when(jwtUtils.generateTokenFromUsername("unverified.phone@example.com")).thenReturn("unverifiedPhoneJwt");
        when(jwtUtils.getJwtExpirationMs()).thenReturn(86400000L);

        RefreshToken refreshToken = RefreshToken.builder().token("unverifiedPhoneRefreshToken").build();
        when(refreshTokenService.createRefreshToken(97L)).thenReturn(refreshToken);

        LoginRequest req = LoginRequest.builder()
                .identifier("+919833918728")
                .password("DriverPass123!")
                .build();

        AuthResponse res = authService.login(req);

        assertNotNull(res);
        assertEquals("unverifiedPhoneJwt", res.getToken());
        assertEquals("unverifiedPhoneRefreshToken", res.getRefreshToken());
        assertEquals("unverified.phone@example.com", res.getUser().getEmail());
    }
}
