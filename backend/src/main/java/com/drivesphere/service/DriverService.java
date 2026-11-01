package com.drivesphere.service;

import com.drivesphere.dto.DriverDto;
import com.drivesphere.entity.Driver;
import com.drivesphere.exception.ResourceNotFoundException;
import com.drivesphere.mapper.EntityDtoMapper;
import com.drivesphere.repository.DriverRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriverService {

    private final DriverRepository driverRepository;
    private final EntityDtoMapper mapper;

    @Transactional(readOnly = true)
    public List<DriverDto> getAllDrivers() {
        return driverRepository.findAll().stream()
                .map(mapper::toDriverDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DriverDto> getAvailableAndApprovedDrivers() {
        java.time.LocalDateTime threshold = java.time.LocalDateTime.now().minusSeconds(120);
        return driverRepository.findAvailableOnlineAndApprovedDrivers(threshold).stream()
                .map(mapper::toDriverDto)
                .map(this::sanitizeForPublic)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DriverDto getDriverById(Long id, Long currentUserId, boolean isStaff) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found with id: " + id));
        DriverDto dto = mapper.toDriverDto(driver);

        boolean isSelf = currentUserId != null && driver.getUser() != null
                && driver.getUser().getId().equals(currentUserId);

        if (isStaff || isSelf) {
            return dto;
        }
        return sanitizeForPublic(dto);
    }

    @Transactional(readOnly = true)
    public DriverDto getDriverById(Long id) {
        return getDriverById(id, null, false);
    }

    @Transactional(readOnly = true)
    public DriverDto getDriverByUserId(Long userId) {
        return driverRepository.findByUserId(userId)
                .map(mapper::toDriverDto)
                .orElseThrow(() -> new ResourceNotFoundException("Driver profile not found for user id: " + userId));
    }

    @Transactional
    public DriverDto toggleAvailability(Long driverId, Long currentUserId, boolean isStaff) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found with id: " + driverId));

        if (!isStaff) {
            if (driver.getUser() == null || !driver.getUser().getId().equals(currentUserId)) {
                throw new AccessDeniedException("Access denied: You can only toggle your own driver availability.");
            }
        }

        driver.setAvailable(!driver.isAvailable());
        return mapper.toDriverDto(driverRepository.save(driver));
    }

    @Transactional
    public DriverDto updateProfile(Long driverId, DriverDto dto, Long currentUserId, boolean isAdmin) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found with id: " + driverId));

        if (!isAdmin) {
            if (driver.getUser() == null || !driver.getUser().getId().equals(currentUserId)) {
                throw new AccessDeniedException("Access denied: You can only update your own driver profile.");
            }
        }

        if (dto.getDailyRate() != null) driver.setDailyRate(dto.getDailyRate());
        if (dto.getLanguages() != null) driver.setLanguages(dto.getLanguages().trim());
        if (dto.getAddress() != null) driver.setAddress(dto.getAddress().trim());
        if (dto.getProfilePhotoUrl() != null) driver.setProfilePhotoUrl(dto.getProfilePhotoUrl().trim());
        if (dto.getExperienceYears() != null) driver.setExperienceYears(dto.getExperienceYears());

        return mapper.toDriverDto(driverRepository.save(driver));
    }

    @Transactional
    public DriverDto updateProfilePhoto(Long driverId, String photoUrl, Long currentUserId, boolean isAdmin) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found with id: " + driverId));

        if (!isAdmin && (driver.getUser() == null || !driver.getUser().getId().equals(currentUserId))) {
            throw new AccessDeniedException("Access denied: You can only update your own driver profile photo.");
        }

        driver.setProfilePhotoUrl(photoUrl != null ? photoUrl.trim() : null);
        return mapper.toDriverDto(driverRepository.save(driver));
    }

    @Transactional
    public DriverDto updateHeartbeat(Long currentUserId) {
        Driver driver = driverRepository.findByUserId(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver profile not found for user id: " + currentUserId));

        if (driver.getUser() == null || !driver.getUser().isActive()) {
            throw new IllegalStateException("Driver account is deactivated.");
        }

        driver.setOnline(true);
        driver.setLastSeenAt(java.time.LocalDateTime.now());
        return mapper.toDriverDto(driverRepository.save(driver));
    }

    @Transactional
    public DriverDto setOnlineStatus(Long currentUserId, boolean online) {
        Driver driver = driverRepository.findByUserId(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver profile not found for user id: " + currentUserId));

        if (online) {
            if (driver.getUser() == null || !driver.getUser().isActive()) {
                throw new IllegalStateException("Driver account is deactivated.");
            }
            if (driver.getVerification() == null || driver.getVerification().getStatus() != com.drivesphere.entity.VerificationStatus.APPROVED) {
                throw new IllegalStateException("Cannot go online. Driver verification must be approved by administration first.");
            }
            driver.setOnline(true);
            driver.setAvailable(true);
            driver.setLastSeenAt(java.time.LocalDateTime.now());
        } else {
            driver.setOnline(false);
            driver.setAvailable(false);
        }

        return mapper.toDriverDto(driverRepository.save(driver));
    }

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void cleanupStaleDriverSessions() {
        java.time.LocalDateTime threshold = java.time.LocalDateTime.now().minusSeconds(120);
        List<Driver> staleDrivers = driverRepository.findAll().stream()
                .filter(d -> d.isOnline() && (d.getLastSeenAt() == null || d.getLastSeenAt().isBefore(threshold)))
                .toList();

        if (!staleDrivers.isEmpty()) {
            for (Driver d : staleDrivers) {
                d.setOnline(false);
                d.setAvailable(false);
            }
            driverRepository.saveAll(staleDrivers);
            log.info("Scheduled cleanup: marked {} stale drivers as offline", staleDrivers.size());
        }
    }

    public DriverDto sanitizeForPublic(DriverDto dto) {
        if (dto == null) return null;
        DriverDto copy = new DriverDto();
        copy.setId(dto.getId());
        copy.setUserId(null); // hide internal user id
        copy.setFullName(dto.getFullName());
        copy.setEmail(null); // sensitive
        copy.setPhoneNumber(null); // sensitive
        copy.setLicenseNumber(null); // sensitive
        copy.setLicenseExpiryDate(null); // sensitive
        copy.setExperienceYears(dto.getExperienceYears());
        copy.setRating(dto.getRating());
        copy.setTotalTrips(dto.getTotalTrips());
        copy.setCancellationRate(dto.getCancellationRate());
        copy.setAvailable(dto.isAvailable());
        copy.setOnline(dto.isOnline());
        copy.setLastSeenAt(dto.getLastSeenAt());
        copy.setDailyRate(dto.getDailyRate());
        copy.setLanguages(dto.getLanguages());
        copy.setAddress(null); // sensitive
        copy.setProfilePhotoUrl(dto.getProfilePhotoUrl());
        copy.setVerificationStatus(dto.getVerificationStatus());
        copy.setTrustScore(dto.getTrustScore());
        copy.setCurrentVehicleId(dto.getCurrentVehicleId());
        copy.setCurrentVehicleName(dto.getCurrentVehicleName());
        return copy;
    }
}
