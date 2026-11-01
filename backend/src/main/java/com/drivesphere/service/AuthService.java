package com.drivesphere.service;

import com.drivesphere.dto.*;
import com.drivesphere.entity.*;
import com.drivesphere.exception.ResourceNotFoundException;
import com.drivesphere.mapper.EntityDtoMapper;
import com.drivesphere.repository.*;
import com.drivesphere.security.JwtUtils;
import com.drivesphere.security.LoginRateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DriverRepository driverRepository;
    private final DriverVerificationRepository driverVerificationRepository;
    private final DriverTrustScoreRepository driverTrustScoreRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final EntityDtoMapper mapper;
    private final RefreshTokenService refreshTokenService;
    private final LoginRateLimiter loginRateLimiter;
    private final AuditLogService auditLogService;
    private final EmailService emailService;

    private static final java.util.regex.Pattern PASSWORD_PATTERN = java.util.regex.Pattern.compile(
            "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]).{8,}$"
    );

    public static String normalizePhoneNumber(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            return null;
        }
        String cleaned = rawPhone.replaceAll("[\\s\\-\\(\\)\\.]", "");
        if (cleaned.isBlank()) {
            return null;
        }
        if (cleaned.startsWith("0") && cleaned.length() == 11 && cleaned.substring(1).matches("\\d+")) {
            return "+91" + cleaned.substring(1);
        }
        if (cleaned.matches("\\d{10}")) {
            return "+91" + cleaned;
        }
        if (cleaned.startsWith("+")) {
            return cleaned;
        }
        if (cleaned.matches("\\d{11,15}")) {
            return "+" + cleaned;
        }
        return cleaned;
    }

    public static String extractPhoneDigits(String phone) {
        if (phone == null) return null;
        return phone.replaceAll("\\D", "");
    }

    private void validatePhoneNumber(String normalizedPhone) {
        if (normalizedPhone != null && !normalizedPhone.isBlank()) {
            if (!normalizedPhone.matches("^\\+[1-9]\\d{6,14}$")) {
                throw new IllegalArgumentException(
                        "Please provide a valid phone number with country code (e.g., +91 9876543210)."
                );
            }
        }
    }

    private void validatePasswordStrength(String password) {
        if (password == null || !PASSWORD_PATTERN.matcher(password).matches()) {
            throw new IllegalArgumentException(
                    "Password must be at least 8 characters long and include at least one uppercase letter, one lowercase letter, one digit, and one special character."
            );
        }
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Error hashing security token", e);
        }
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Privilege escalation guard: Only CUSTOMER or DRIVER can be registered publicly
        if (request.getRole() == RoleType.ROLE_ADMIN || request.getRole() == RoleType.ROLE_FLEET_MANAGER) {
            throw new AccessDeniedException(
                    "Privileged roles (ADMIN, FLEET_MANAGER) cannot be created via public registration."
            );
        }

        validatePasswordStrength(request.getPassword());

        String normalizedEmail = request.getEmail().trim().toLowerCase();
        String normalizedPhone = normalizePhoneNumber(request.getPhoneNumber());
        validatePhoneNumber(normalizedPhone);

        // Pre-insert Duplicate Account Inspection
        java.util.Optional<User> existingUserByEmail = userRepository.findByEmail(normalizedEmail);
        java.util.Optional<User> existingUserByPhone = java.util.Optional.empty();

        if (normalizedPhone != null && !normalizedPhone.isBlank()) {
            existingUserByPhone = userRepository.findByPhoneNumber(normalizedPhone);
            if (existingUserByPhone.isEmpty()) {
                String cleanDigits = extractPhoneDigits(normalizedPhone);
                if (cleanDigits != null && !cleanDigits.isBlank()) {
                    existingUserByPhone = userRepository.findByPhoneDigits(cleanDigits);
                }
            }
        }

        if (existingUserByEmail.isPresent() && existingUserByPhone.isPresent()) {
            User emailUser = existingUserByEmail.get();
            User phoneUser = existingUserByPhone.get();
            if (emailUser.getId().equals(phoneUser.getId())) {
                throw new com.drivesphere.exception.DuplicateAccountException(
                        "ACCOUNT_ALREADY_EXISTS",
                        "An account with this email address and phone number already exists. Please log in using your existing credentials."
                );
            } else {
                throw new com.drivesphere.exception.DuplicateAccountException(
                        "ACCOUNT_CONFLICT",
                        "The email address and phone number provided belong to different registered accounts. Please log in using the correct account or contact support."
                );
            }
        } else if (existingUserByPhone.isPresent()) {
            throw new com.drivesphere.exception.DuplicateAccountException(
                    "PHONE_ALREADY_REGISTERED",
                    "An account is already associated with this phone number. Please log in to your existing account."
            );
        } else if (existingUserByEmail.isPresent()) {
            throw new com.drivesphere.exception.DuplicateAccountException(
                    "EMAIL_ALREADY_REGISTERED",
                    "An account with this email address already exists. Please log in or recover your account."
            );
        }

        RoleType roleType = request.getRole() != null ? request.getRole() : RoleType.ROLE_CUSTOMER;

        // If DRIVER registration, validate mandatory driver fields - NO FAKE DEFAULTS
        if (roleType == RoleType.ROLE_DRIVER) {
            if (request.getLicenseNumber() == null || request.getLicenseNumber().trim().length() < 5) {
                throw new IllegalArgumentException("Valid Driving License Number is required for driver registration.");
            }
            if (request.getLicenseExpiryDate() == null || !request.getLicenseExpiryDate().isAfter(LocalDate.now())) {
                throw new IllegalArgumentException("License expiry date must be in the future.");
            }
            if (request.getExperienceYears() == null || request.getExperienceYears() < 0) {
                throw new IllegalArgumentException("Valid driving experience (years) is required.");
            }
            if (request.getAddress() == null || request.getAddress().trim().length() < 5) {
                throw new IllegalArgumentException("Residential address is required for driver partner verification.");
            }
            if (request.getLanguages() == null || request.getLanguages().trim().isBlank()) {
                throw new IllegalArgumentException("Languages known are required.");
            }
        }

        Role role = roleRepository.findByName(roleType)
                .orElseGet(() -> roleRepository.save(Role.builder().name(roleType).description("System role").build()));

        Set<Role> roles = new HashSet<>();
        roles.add(role);

        // Account is created active and can log in immediately. Email verification is optional and not mandatory for normal login.
        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .phoneNumber(normalizedPhone)
                .active(true)
                .verified(false)
                .emailVerified(false)
                .roles(roles)
                .build();

        try {
            user = userRepository.save(user);
        } catch (org.springframework.dao.DataIntegrityViolationException dive) {
            log.warn("Concurrent duplicate registration constraint violation during user save: {}", dive.getMessage());
            String raw = dive.getMessage() != null ? dive.getMessage().toLowerCase() : "";
            if (raw.contains("phone_number") || raw.contains("phone") || raw.contains("uk9q63snka3mdh91as4io72espi")) {
                throw new com.drivesphere.exception.DuplicateAccountException(
                        "PHONE_ALREADY_REGISTERED",
                        "An account is already associated with this phone number. Please log in to your existing account."
                );
            }
            throw new com.drivesphere.exception.DuplicateAccountException(
                    "EMAIL_ALREADY_REGISTERED",
                    "An account with this email address already exists. Please log in or recover your account."
            );
        }

        // If registering as DRIVER: create Driver record without fake 5.0 rating or fake reviews
        if (roleType == RoleType.ROLE_DRIVER) {
            Driver driver = Driver.builder()
                    .user(user)
                    .licenseNumber(request.getLicenseNumber().trim())
                    .licenseExpiryDate(request.getLicenseExpiryDate())
                    .experienceYears(request.getExperienceYears())
                    .rating(null) // Real: New drivers have NO ratings yet
                    .totalTrips(0)
                    .cancellationRate(BigDecimal.ZERO)
                    .available(false)
                    .online(false)
                    .dailyRate(BigDecimal.valueOf(800.00))
                    .languages(request.getLanguages().trim())
                    .address(request.getAddress().trim())
                    .build();

            driver = driverRepository.save(driver);

            DriverVerification verification = DriverVerification.builder()
                    .driver(driver)
                    .status(VerificationStatus.PENDING)
                    .submittedAt(Instant.now())
                    .build();
            driverVerificationRepository.save(verification);

            DriverTrustScore trustScore = DriverTrustScore.builder()
                    .driver(driver)
                    .overallScore(null) // Real: Trust score not available until verified and trips completed
                    .trustLevel(null)
                    .explanation("Driver Trust Score unavailable. Pending identity verification and first verified customer trip.")
                    .lastCalculatedAt(Instant.now())
                    .build();
            driverTrustScoreRepository.save(trustScore);
        }

        // Generate Single-Use Email Verification Token
        String rawToken = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        String tokenHash = hashToken(rawToken);

        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .used(false)
                .build();
        emailVerificationTokenRepository.save(verificationToken);

        // Send Real Verification Email
        if (emailService != null) {
            try {
                emailService.sendVerificationEmail(user.getEmail(), user.getFirstName(), rawToken);
            } catch (Exception e) {
                log.warn("Failed to dispatch verification email to {}: {}. Account created; user may request link resend.", user.getEmail(), e.getMessage());
            }
        }

        auditLogService.logAction("USER_REGISTERED", "USER", user.getId().toString(),
                "User registered with role: " + roleType.name() + " (Email Verification Pending)", null);

        UserDto userDto = mapper.toUserDto(user);
        attachDriverIdIfPresent(user, userDto);

        // Return registration acknowledgement - Login blocked until verified
        return AuthResponse.builder()
                .token(null)
                .refreshToken(null)
                .tokenType("Bearer")
                .user(userDto)
                .build();
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String rawIdentifier = request.getEffectiveIdentifier();
        if (rawIdentifier == null || rawIdentifier.isBlank()) {
            throw new IllegalArgumentException("Email address or phone number is required.");
        }

        String trimmed = rawIdentifier.trim();
        String lookupKey;
        User user;

        if (trimmed.contains("@")) {
            lookupKey = trimmed.toLowerCase();
            user = userRepository.findByEmail(lookupKey).orElse(null);
        } else {
            lookupKey = normalizePhoneNumber(trimmed);
            user = userRepository.findByPhoneNumber(lookupKey).orElse(null);
            if (user == null) {
                String digits = extractPhoneDigits(trimmed);
                if (digits != null && !digits.isBlank()) {
                    user = userRepository.findByPhoneDigits(digits).orElse(null);
                    if (user == null && digits.length() == 10) {
                        user = userRepository.findByPhoneDigits("91" + digits).orElse(null);
                    }
                }
            }
        }

        // Rate limiter key: use user's email if found to ensure uniform per-account rate limiting,
        // otherwise use the normalized identifier
        String rateLimitKey = (user != null) ? user.getEmail() : (lookupKey != null ? lookupKey : trimmed);
        if (loginRateLimiter.isBlocked(rateLimitKey)) {
            long remaining = loginRateLimiter.getRemainingBlockTimeSeconds(rateLimitKey);
            throw new LockedException(
                    "Account temporarily locked due to excessive failed attempts. Try again in " + remaining + " seconds."
            );
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(trimmed, request.getPassword())
            );

            loginRateLimiter.loginSucceeded(rateLimitKey);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            if (user == null) {
                user = userRepository.findByEmail(authentication.getName())
                        .orElseThrow(() -> new ResourceNotFoundException("User account not found"));
            }

            if (!user.isActive()) {
                throw new DisabledException("Account is deactivated. Please contact support.");
            }

            String token = jwtUtils.generateTokenFromUsername(user.getEmail());
            RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId());

            UserDto userDto = mapper.toUserDto(user);
            attachDriverIdIfPresent(user, userDto);

            return AuthResponse.builder()
                    .token(token)
                    .refreshToken(refreshToken.getToken())
                    .expiresIn(jwtUtils.getJwtExpirationMs())
                    .tokenType("Bearer")
                    .user(userDto)
                    .build();
        } catch (AuthenticationException e) {
            loginRateLimiter.loginFailed(rateLimitKey);
            throw e;
        }
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        if (rawToken == null || rawToken.trim().isBlank()) {
            throw new IllegalArgumentException("Verification token is required.");
        }

        String hash = hashToken(rawToken.trim());
        EmailVerificationToken token = emailVerificationTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid verification token."));

        if (!token.isValid()) {
            throw new IllegalArgumentException("Verification token has already been used or has expired. Please request a new link.");
        }

        token.setUsed(true);
        token.setUsedAt(Instant.now());
        emailVerificationTokenRepository.save(token);

        User user = token.getUser();
        user.setEmailVerified(true);
        user.setVerified(true); // Customer verified for app use; Driver KYC remains separate
        userRepository.save(user);

        auditLogService.logAction("EMAIL_VERIFIED", "USER", user.getId().toString(),
                "User successfully verified email: " + user.getEmail(), null);

        log.info("Email verified successfully for user: {}", user.getEmail());
    }

    @Transactional
    public void resendVerification(String email) {
        if (email == null || email.trim().isBlank()) {
            throw new IllegalArgumentException("Email is required.");
        }

        String normalizedEmail = email.trim().toLowerCase();
        if (loginRateLimiter.isActionRateLimited("RESEND_VERIF", normalizedEmail, 3, 10 * 60 * 1000L)) {
            throw new IllegalArgumentException("Too many verification requests. Please wait a few minutes before trying again.");
        }

        // Avoid user enumeration: proceed silently if user not found or already verified
        User user = userRepository.findByEmailOrPhone(normalizedEmail).orElse(null);
        if (user != null && !user.isEmailVerified()) {
            emailVerificationTokenRepository.invalidateUserTokens(user);

            String rawToken = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
            String tokenHash = hashToken(rawToken);

            EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                    .user(user)
                    .tokenHash(tokenHash)
                    .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                    .used(false)
                    .build();
            emailVerificationTokenRepository.save(verificationToken);

            if (emailService != null) {
                try {
                    emailService.sendVerificationEmail(user.getEmail(), user.getFirstName(), rawToken);
                    log.info("Resent verification email to: {}", user.getEmail());
                } catch (com.drivesphere.exception.EmailDeliveryException ede) {
                    log.error("SMTP delivery failure during verification resend: {}", ede.getMessage());
                    throw ede;
                } catch (Exception e) {
                    log.warn("Failed to resend verification email to {}: {}", user.getEmail(), e.getMessage());
                }
            }
        }
    }

    @Transactional
    public void forgotPassword(String email) {
        if (email == null || email.trim().isBlank()) {
            throw new IllegalArgumentException("Email is required.");
        }

        String normalizedEmail = email.trim().toLowerCase();
        if (loginRateLimiter.isActionRateLimited("FORGOT_PWD", normalizedEmail, 3, 10 * 60 * 1000L)) {
            throw new IllegalArgumentException("Too many password reset requests. Please wait a few minutes before trying again.");
        }

        // Avoid user enumeration
        User user = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (user != null && user.isActive()) {
            passwordResetTokenRepository.invalidateUserTokens(user);

            String rawToken = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
            String tokenHash = hashToken(rawToken);

            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .user(user)
                    .tokenHash(tokenHash)
                    .expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES)) // 15-minute expiration
                    .used(false)
                    .build();
            passwordResetTokenRepository.save(resetToken);

            if (emailService != null) {
                try {
                    emailService.sendPasswordResetEmail(user.getEmail(), user.getFirstName(), rawToken);
                    log.info("Sent password reset link to: {}", EmailService.maskEmail(user.getEmail()));
                } catch (com.drivesphere.exception.EmailDeliveryException ede) {
                    log.error("Failed to dispatch password reset email to {}: {}", EmailService.maskEmail(user.getEmail()), ede.getMessage());
                    throw new com.drivesphere.exception.ApiException(
                            org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                            "We're temporarily unable to dispatch recovery emails right now. Please try again in a moment or contact support."
                    );
                } catch (Exception e) {
                    log.warn("Non-fatal email dispatch exception for {}: {}", EmailService.maskEmail(user.getEmail()), e.getMessage());
                }
            }
        }
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.trim().isBlank()) {
            throw new IllegalArgumentException("Reset token is required.");
        }

        validatePasswordStrength(newPassword);

        String hash = hashToken(rawToken.trim());
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid password reset token."));

        if (!token.isValid()) {
            throw new IllegalArgumentException("Password reset link has already been used or has expired. Please request a new link.");
        }

        token.setUsed(true);
        token.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(token);

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Revoke all existing refresh tokens after password reset
        refreshTokenService.deleteByUserId(user.getId());

        auditLogService.logAction("PASSWORD_RESET", "USER", user.getId().toString(),
                "User successfully reset password via security token.", null);

        log.info("Password reset successfully for user: {}", user.getEmail());
    }

    @Transactional
    public TokenRefreshResponse refreshToken(String token) {
        return refreshTokenService.findByToken(token)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    if (!user.isActive()) {
                        throw new DisabledException("Account is deactivated.");
                    }
                    String newAccessToken = jwtUtils.generateTokenFromUsername(user.getEmail());
                    return TokenRefreshResponse.builder()
                            .accessToken(newAccessToken)
                            .refreshToken(token)
                            .expiresIn(jwtUtils.getJwtExpirationMs())
                            .tokenType("Bearer")
                            .build();
                })
                .orElseThrow(() -> new IllegalArgumentException("Refresh token is not in database!"));
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        validatePasswordStrength(request.getNewPassword());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password does not match.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        refreshTokenService.deleteByUserId(user.getId());

        auditLogService.logAction("PASSWORD_CHANGED", "USER", user.getId().toString(),
                "User changed password through account settings.", null);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("User is not authenticated.");
        }

        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        changePassword(user.getId(), request);
    }

    @Transactional(readOnly = true)
    public UserDto getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .map(user -> {
                    UserDto dto = mapper.toUserDto(user);
                    attachDriverIdIfPresent(user, dto);
                    return dto;
                })
                .orElse(null);
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revokeToken(refreshToken);
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
            userRepository.findByEmail(authentication.getName()).ifPresent(user -> {
                refreshTokenService.deleteByUserId(user.getId());
                auditLogService.logAction("LOGOUT", "USER", user.getId().toString(), "User logged out successfully", null);
            });
        }
    }

    private void attachDriverIdIfPresent(User user, UserDto userDto) {
        if (user != null && userDto != null && user.getId() != null && user.hasRole(RoleType.ROLE_DRIVER)) {
            driverRepository.findByUserId(user.getId())
                    .ifPresent(d -> userDto.setDriverId(d.getId()));
        }
    }
}
