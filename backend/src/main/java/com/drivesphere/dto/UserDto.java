package com.drivesphere.dto;

import com.drivesphere.entity.RoleType;
import lombok.*;

import java.time.Instant;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDto {

    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private String fullName;
    private String phoneNumber;
    private String avatarUrl;
    private RoleType primaryRole;
    private Set<RoleType> roles;
    private boolean active;
    private boolean verified;
    private Long driverId; // Non-null if user is a driver
    private Instant createdAt;
}
