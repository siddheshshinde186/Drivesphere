package com.drivesphere.service;

import com.drivesphere.dto.RegisterRequest;
import com.drivesphere.entity.Role;
import com.drivesphere.entity.RoleType;
import com.drivesphere.entity.User;
import com.drivesphere.mapper.EntityDtoMapper;
import com.drivesphere.repository.*;
import com.drivesphere.security.JwtUtils;
import com.drivesphere.security.LoginRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Automated tests for Admin Access Control & Role Governance (Priority 3):
 * 1. Public registration cannot assign ROLE_ADMIN.
 * 2. Public registration cannot assign ROLE_FLEET_MANAGER.
 * 3. Legitimate customer and driver registrations succeed without granting administrative privilege.
 * 4. Administrator privileges provisioned only through trusted explicit role assignments.
 */
class AdminAccessControlTests {

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
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepo = Mockito.mock(UserRepository.class);
        roleRepo = Mockito.mock(RoleRepository.class);
        driverRepo = Mockito.mock(DriverRepository.class);
        driverVerificationRepo = Mockito.mock(DriverVerificationRepository.class);
        driverTrustScoreRepo = Mockito.mock(DriverTrustScoreRepository.class);
        emailTokenRepo = Mockito.mock(EmailVerificationTokenRepository.class);
        passwordResetTokenRepo = Mockito.mock(PasswordResetTokenRepository.class);
        refreshTokenService = Mockito.mock(RefreshTokenService.class);
        passwordEncoder = Mockito.mock(PasswordEncoder.class);
        authManager = Mockito.mock(AuthenticationManager.class);
        jwtUtils = Mockito.mock(JwtUtils.class);
        rateLimiter = new LoginRateLimiter();
        auditLogService = Mockito.mock(AuditLogService.class);
        emailService = Mockito.mock(EmailService.class);

        authService = new AuthService(
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
    @DisplayName("P3.1: Public registration requesting ROLE_ADMIN must be strictly rejected with 403 AccessDeniedException")
    void publicRegistrationRejectsRoleAdmin() {
        RegisterRequest req = RegisterRequest.builder()
                .firstName("Attacker")
                .lastName("User")
                .email("hacker@example.com")
                .password("Password@123")
                .phoneNumber("+91 9999999999")
                .role(RoleType.ROLE_ADMIN)
                .build();

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                authService.register(req)
        );
        assertTrue(ex.getMessage().contains("Privileged roles (ADMIN, FLEET_MANAGER) cannot be created via public registration"));
    }

    @Test
    @DisplayName("P3.2: Public registration requesting ROLE_FLEET_MANAGER must be strictly rejected with 403 AccessDeniedException")
    void publicRegistrationRejectsRoleFleetManager() {
        RegisterRequest req = RegisterRequest.builder()
                .firstName("Unauthorized")
                .lastName("Manager")
                .email("wannabe_manager@example.com")
                .password("Password@123")
                .phoneNumber("+91 9888888888")
                .role(RoleType.ROLE_FLEET_MANAGER)
                .build();

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                authService.register(req)
        );
        assertTrue(ex.getMessage().contains("Privileged roles (ADMIN, FLEET_MANAGER) cannot be created via public registration"));
    }

    @Test
    @DisplayName("P3.3: Legitimate public registration with ROLE_CUSTOMER succeeds and assigns only ROLE_CUSTOMER")
    void publicRegistrationAllowsCustomer() {
        RegisterRequest req = RegisterRequest.builder()
                .firstName("Priya")
                .lastName("Sharma")
                .email("priya.customer@example.com")
                .password("SecurePass@123")
                .phoneNumber("+91 9123456789")
                .role(RoleType.ROLE_CUSTOMER)
                .build();

        Role customerRole = Role.builder().name(RoleType.ROLE_CUSTOMER).build();
        when(roleRepo.findByName(RoleType.ROLE_CUSTOMER)).thenReturn(Optional.of(customerRole));
        when(userRepo.findByEmail("priya.customer@example.com")).thenReturn(Optional.empty());
        when(userRepo.findByPhoneNumber("+91 9123456789")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("SecurePass@123")).thenReturn("encodedPasswordHash");
        when(userRepo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(500L);
            return u;
        });

        var response = authService.register(req);
        assertNotNull(response);
        assertEquals("priya.customer@example.com", response.getUser().getEmail());
        assertEquals(RoleType.ROLE_CUSTOMER, response.getUser().getPrimaryRole());
        assertTrue(response.getUser().getRoles().contains(RoleType.ROLE_CUSTOMER));
        assertFalse(response.getUser().getRoles().contains(RoleType.ROLE_ADMIN));
        assertFalse(response.getUser().getRoles().contains(RoleType.ROLE_FLEET_MANAGER));
    }

    @Test
    @DisplayName("P3.4: Initial Admin Provisioning creates hashed, verified Admin and logs audit trail when no admin exists")
    void testInitialAdminProvisioning_CreatesAdminWhenNoneExists() {
        Role adminRole = Role.builder().name(RoleType.ROLE_ADMIN).description("Platform Administrator").build();
        when(roleRepo.count()).thenReturn(1L);
        when(roleRepo.findByName(RoleType.ROLE_ADMIN)).thenReturn(Optional.of(adminRole));
        when(userRepo.findAll()).thenReturn(java.util.Collections.emptyList());
        when(userRepo.findByEmail("bootstrap.admin@drivesphere.prod")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("SecureAdminSecret2026!")).thenReturn("bcrypt_hashed_admin_pass");

        com.drivesphere.config.DataInitializer initializer = new com.drivesphere.config.DataInitializer(
                roleRepo, userRepo, Mockito.mock(VehicleCategoryRepository.class),
                Mockito.mock(VehicleRepository.class), driverRepo, driverVerificationRepo,
                driverTrustScoreRepo, Mockito.mock(BookingRepository.class),
                Mockito.mock(PaymentRepository.class), Mockito.mock(MaintenanceRepository.class),
                Mockito.mock(ReviewRepository.class), passwordEncoder
        );
        initializer.setDemoDataEnabled(false);
        initializer.setBootstrapAdminEmail("bootstrap.admin@drivesphere.prod");
        initializer.setBootstrapAdminPassword("SecureAdminSecret2026!");
        initializer.setAuditLogService(auditLogService);

        when(userRepo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(999L);
            return u;
        });

        initializer.run();

        Mockito.verify(userRepo, Mockito.times(1)).save(Mockito.argThat(u ->
                "bootstrap.admin@drivesphere.prod".equals(u.getEmail()) &&
                "bcrypt_hashed_admin_pass".equals(u.getPasswordHash()) &&
                u.hasRole(RoleType.ROLE_ADMIN) &&
                u.isEmailVerified() &&
                u.isActive()
        ));
        Mockito.verify(auditLogService, Mockito.times(1)).logAction(
                Mockito.eq("ADMIN_BOOTSTRAP_CREATED"),
                Mockito.eq("USER"),
                Mockito.eq("999"),
                Mockito.anyString(),
                Mockito.eq("127.0.0.1")
        );
    }

    @Test
    @DisplayName("P3.5: One-Time Guarantee: Initial Admin Provisioning skips execution if admin already exists")
    void testInitialAdminProvisioning_SkipsWhenAdminAlreadyExists() {
        Role adminRole = Role.builder().name(RoleType.ROLE_ADMIN).description("Platform Administrator").build();
        User existingAdmin = User.builder().email("existing.admin@drivesphere.com").roles(Set.of(adminRole)).build();
        existingAdmin.setId(1L);

        when(roleRepo.count()).thenReturn(1L);
        when(roleRepo.findByName(RoleType.ROLE_ADMIN)).thenReturn(Optional.of(adminRole));
        when(userRepo.findAll()).thenReturn(List.of(existingAdmin));

        com.drivesphere.config.DataInitializer initializer = new com.drivesphere.config.DataInitializer(
                roleRepo, userRepo, Mockito.mock(VehicleCategoryRepository.class),
                Mockito.mock(VehicleRepository.class), driverRepo, driverVerificationRepo,
                driverTrustScoreRepo, Mockito.mock(BookingRepository.class),
                Mockito.mock(PaymentRepository.class), Mockito.mock(MaintenanceRepository.class),
                Mockito.mock(ReviewRepository.class), passwordEncoder
        );
        initializer.setDemoDataEnabled(false);
        initializer.setBootstrapAdminEmail("new.admin@drivesphere.prod");
        initializer.setBootstrapAdminPassword("SecureAdminSecret2026!");
        initializer.setAuditLogService(auditLogService);

        initializer.run();

        Mockito.verify(userRepo, Mockito.never()).save(Mockito.any(User.class));
        Mockito.verify(auditLogService, Mockito.never()).logAction(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
    }
}
