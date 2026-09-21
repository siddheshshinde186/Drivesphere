package com.drivesphere.service;

import com.drivesphere.dto.UserDto;
import com.drivesphere.entity.Role;
import com.drivesphere.entity.RoleType;
import com.drivesphere.entity.User;
import com.drivesphere.exception.ResourceNotFoundException;
import com.drivesphere.mapper.EntityDtoMapper;
import com.drivesphere.repository.RoleRepository;
import com.drivesphere.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
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
        if (firstName != null && !firstName.isBlank()) user.setFirstName(firstName.trim());
        if (lastName != null && !lastName.isBlank()) user.setLastName(lastName.trim());
        if (phoneNumber != null) user.setPhoneNumber(phoneNumber.trim());
        if (avatarUrl != null) user.setAvatarUrl(avatarUrl.trim());
        return mapper.toUserDto(userRepository.save(user));
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
