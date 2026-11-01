package com.drivesphere.security;

import com.drivesphere.entity.User;
import com.drivesphere.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(@NonNull String identifier) throws UsernameNotFoundException {
        String trimmed = identifier.trim();
        java.util.Optional<User> userOpt;

        if (trimmed.contains("@")) {
            userOpt = userRepository.findByEmail(trimmed.toLowerCase());
        } else {
            // Identifier is a phone number
            String normalizedPhone = com.drivesphere.service.AuthService.normalizePhoneNumber(trimmed);
            userOpt = userRepository.findByPhoneNumber(normalizedPhone);
            if (userOpt.isEmpty()) {
                String digits = com.drivesphere.service.AuthService.extractPhoneDigits(trimmed);
                if (digits != null && !digits.isBlank()) {
                    userOpt = userRepository.findByPhoneDigits(digits);
                    if (userOpt.isEmpty() && digits.length() == 10) {
                        userOpt = userRepository.findByPhoneDigits("91" + digits);
                    }
                }
            }
        }

        User user = userOpt.orElseThrow(() -> new UsernameNotFoundException("User not found with identifier: " + identifier));

        List<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName().name()))
                .collect(Collectors.toList());

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(authorities)
                .disabled(!user.isActive())
                .build();
    }
}
