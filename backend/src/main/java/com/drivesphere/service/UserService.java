package com.drivesphere.service;

import com.drivesphere.dto.UserDto;
import com.drivesphere.dto.UserProfileUpdateRequest;
import com.drivesphere.dto.UserStatsDto;
import com.drivesphere.entity.*;
import com.drivesphere.exception.ResourceNotFoundException;
import com.drivesphere.mapper.EntityDtoMapper;
import com.drivesphere.repository.BookingRepository;
import com.drivesphere.repository.RoleRepository;
import com.drivesphere.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final BookingRepository bookingRepository;
    private final EntityDtoMapper mapper;

    @Transactional(readOnly = true)
    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(mapper::toUserDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserDto getUserById(Long id) {
        return userRepository.findById(id)
                .map(mapper::toUserDto)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    @Transactional
    public UserDto toggleUserActiveStatus(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        user.setActive(!user.isActive());
        return mapper.toUserDto(userRepository.save(user));
    }

    @Transactional
    public UserDto updateUserProfile(Long id, String firstName, String lastName, String phoneNumber, String avatarUrl) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        if (firstName != null && !firstName.isBlank()) {
            user.setFirstName(firstName.trim());
        }
        if (lastName != null && !lastName.isBlank()) {
            user.setLastName(lastName.trim());
        }
        if (phoneNumber != null) {
            String cleanPhone = phoneNumber.trim();
            if (!cleanPhone.isBlank()) {
                Optional<User> existingPhone = userRepository.findByPhoneNumber(cleanPhone);
                if (existingPhone.isPresent() && !existingPhone.get().getId().equals(user.getId())) {
                    throw new IllegalArgumentException("Phone number is already associated with another account.");
                }
                user.setPhoneNumber(cleanPhone);
            } else {
                user.setPhoneNumber(null);
            }
        }
        if (avatarUrl != null) {
            user.setAvatarUrl(avatarUrl.trim().isBlank() ? null : avatarUrl.trim());
        }

        return mapper.toUserDto(userRepository.save(user));
    }

    @Transactional
    public UserDto updateUserProfile(Long id, UserProfileUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Profile update request cannot be null.");
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        if (request.getFirstName() != null && !request.getFirstName().isBlank()) {
            user.setFirstName(request.getFirstName().trim());
        }
        if (request.getLastName() != null && !request.getLastName().isBlank()) {
            user.setLastName(request.getLastName().trim());
        }
        if (request.getPhoneNumber() != null) {
            String cleanPhone = request.getPhoneNumber().trim();
            if (!cleanPhone.isBlank()) {
                Optional<User> existingPhone = userRepository.findByPhoneNumber(cleanPhone);
                if (existingPhone.isPresent() && !existingPhone.get().getId().equals(user.getId())) {
                    throw new IllegalArgumentException("Phone number is already associated with another account.");
                }
                user.setPhoneNumber(cleanPhone);
            } else {
                user.setPhoneNumber(null);
            }
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl().trim().isBlank() ? null : request.getAvatarUrl().trim());
        }
        if (request.getPreferredVehicleType() != null) {
            user.setPreferredVehicleType(request.getPreferredVehicleType().trim());
        }
        if (request.getDriverPreference() != null) {
            user.setDriverPreference(request.getDriverPreference().trim());
        }

        return mapper.toUserDto(userRepository.save(user));
    }

    @Transactional
    public UserDto updateAvatar(Long id, String avatarUrl) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        user.setAvatarUrl(avatarUrl != null ? avatarUrl.trim() : null);
        return mapper.toUserDto(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public UserStatsDto getUserStats(Long userId) {
        List<Booking> bookings = bookingRepository.findByCustomerIdOrderByCreatedAtDesc(userId);
        long totalBookings = bookings.size();

        long activeRentals = bookings.stream()
                .filter(b -> b.getBookingStatus() == BookingStatus.ACTIVE || b.getBookingStatus() == BookingStatus.CONFIRMED || b.getBookingStatus() == BookingStatus.HELD)
                .count();

        long completedTrips = bookings.stream()
                .filter(b -> b.getBookingStatus() == BookingStatus.COMPLETED)
                .count();

        BigDecimal totalSpent = bookings.stream()
                .filter(b -> b.getPaymentStatus() == PaymentStatus.PAID && b.getTotalAmount() != null)
                .map(Booking::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return UserStatsDto.builder()
                .totalBookings(totalBookings)
                .activeRentals(activeRentals)
                .completedTrips(completedTrips)
                .totalSpent(totalSpent)
                .build();
    }

    @Transactional
    public UserDto assignRole(Long userId, RoleType roleType) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        Role role = roleRepository.findByName(roleType)
                .orElseGet(() -> roleRepository.save(Role.builder().name(roleType).build()));

        user.getRoles().add(role);
        return mapper.toUserDto(userRepository.save(user));
    }
}

