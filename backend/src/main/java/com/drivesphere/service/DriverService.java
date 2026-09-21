package com.drivesphere.service;

import com.drivesphere.dto.DriverDto;
import com.drivesphere.entity.Driver;
import com.drivesphere.exception.ResourceNotFoundException;
import com.drivesphere.mapper.EntityDtoMapper;
import com.drivesphere.repository.DriverRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

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
        return driverRepository.findAvailableAndApprovedDrivers().stream()
                .map(mapper::toDriverDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DriverDto getDriverById(Long id) {
        return driverRepository.findById(id)
                .map(mapper::toDriverDto)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public DriverDto getDriverByUserId(Long userId) {
        return driverRepository.findByUserId(userId)
                .map(mapper::toDriverDto)
                .orElseThrow(() -> new ResourceNotFoundException("Driver profile not found for user id: " + userId));
    }

    @Transactional
    public DriverDto toggleAvailability(Long driverId) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found with id: " + driverId));
        driver.setAvailable(!driver.isAvailable());
        return mapper.toDriverDto(driverRepository.save(driver));
    }

    @Transactional
    public DriverDto updateProfile(Long driverId, DriverDto dto) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found with id: " + driverId));

        if (dto.getDailyRate() != null) driver.setDailyRate(dto.getDailyRate());
        if (dto.getLanguages() != null) driver.setLanguages(dto.getLanguages().trim());
        if (dto.getAddress() != null) driver.setAddress(dto.getAddress().trim());
        if (dto.getProfilePhotoUrl() != null) driver.setProfilePhotoUrl(dto.getProfilePhotoUrl().trim());
        if (dto.getExperienceYears() != null) driver.setExperienceYears(dto.getExperienceYears());

        return mapper.toDriverDto(driverRepository.save(driver));
    }
}
