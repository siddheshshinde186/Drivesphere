package com.drivesphere.service;

import com.drivesphere.dto.AuthResponse;
import com.drivesphere.dto.ChangePasswordRequest;
import com.drivesphere.dto.LoginRequest;
import com.drivesphere.dto.RegisterRequest;
import com.drivesphere.dto.UserDto;
import com.drivesphere.entity.*;
import com.drivesphere.exception.ResourceNotFoundException;
import com.drivesphere.mapper.EntityDtoMapper;
import com.drivesphere.repository.*;
import com.drivesphere.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DriverRepository driverRepository;
    private final DriverVerificationRepository driverVerificationRepository;
    private final DriverTrustScoreRepository driverTrustScoreRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final EntityDtoMapper mapper;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already in use: " + request.getEmail());
        }

        RoleType roleType = request.getRole() != null ? request.getRole() : RoleType.ROLE_CUSTOMER;
        Role role = roleRepository.findByName(roleType)
                .orElseGet(() -> roleRepository.save(Role.builder().name(roleType).description("System role").build()));

        Set<Role> roles = new HashSet<>();
        roles.add(role);

        User user = User.builder()
                .email(request.getEmail().trim().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .phoneNumber(request.getPhoneNumber())
                .active(true)
                .verified(roleType != RoleType.ROLE_DRIVER) // Customers are auto-verified; Drivers undergo document verification
                .roles(roles)
                .build();

        user = userRepository.save(user);

        // If registering as DRIVER, create Driver record and initial DriverVerification & TrustScore
        if (roleType == RoleType.ROLE_DRIVER) {
            String license = request.getLicenseNumber() != null ? request.getLicenseNumber() : "DL-" + System.currentTimeMillis();
            LocalDate expiry = request.getLicenseExpiryDate() != null ? request.getLicenseExpiryDate() : LocalDate.now().plusYears(5);
            int exp = request.getExperienceYears() != null ? request.getExperienceYears() : 2;

            Driver driver = Driver.builder()
                    .user(user)
                    .licenseNumber(license)
                    .licenseExpiryDate(expiry)
                    .experienceYears(exp)
                    .rating(BigDecimal.valueOf(5.00))
                    .totalTrips(0)
                    .cancellationRate(BigDecimal.ZERO)
                    .available(false) // Not available until verified
                    .dailyRate(BigDecimal.valueOf(800.00))
                    .languages(request.getLanguages() != null ? request.getLanguages() : "English, Hindi")
                    .address(request.getAddress())
                    .build();

            driver = driverRepository.save(driver);

            DriverVerification verification = DriverVerification.builder()
                    .driver(driver)
                    .status(VerificationStatus.PENDING)
                    .submittedAt(Instant.now())
                    .drivingSkillScore(80)
                    .build();
            driverVerificationRepository.save(verification);

            DriverTrustScore trustScore = DriverTrustScore.builder()
                    .driver(driver)
                    .overallScore(BigDecimal.valueOf(65.00))
                    .trustLevel(TrustLevel.AVERAGE)
                    .verificationFactor(BigDecimal.valueOf(10.00))
                    .experienceFactor(BigDecimal.valueOf(15.00))
                    .ratingFactor(BigDecimal.valueOf(25.00))
                    .completionFactor(BigDecimal.valueOf(15.00))
                    .explanation("Initial provisional trust score for newly registered driver pending document verification.")
                    .lastCalculatedAt(Instant.now())
                    .build();
            driverTrustScoreRepository.save(trustScore);
        }

        String token = jwtUtils.generateTokenFromUsername(user.getEmail());
        UserDto userDto = mapper.toUserDto(user);
        attachDriverIdIfPresent(user, userDto);

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .user(userDto)
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail().trim().toLowerCase(), request.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String token = jwtUtils.generateTokenFromUsername(authentication.getName());

        User user = userRepository.findByEmail(request.getEmail().trim().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + request.getEmail()));

        UserDto userDto = mapper.toUserDto(user);
        attachDriverIdIfPresent(user, userDto);

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .user(userDto)
                .build();
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password does not match");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public UserDto getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")) {
            return null;
        }

        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found"));

        UserDto dto = mapper.toUserDto(user);
        attachDriverIdIfPresent(user, dto);
        return dto;
    }

    private void attachDriverIdIfPresent(User user, UserDto userDto) {
        if (user.hasRole(RoleType.ROLE_DRIVER)) {
            driverRepository.findByUser(user).ifPresent(d -> userDto.setDriverId(d.getId()));
        }
    }
}
