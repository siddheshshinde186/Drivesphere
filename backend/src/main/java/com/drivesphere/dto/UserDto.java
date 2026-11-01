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
    private boolean emailVerified;
    private Long driverId; // Non-null if user is a driver
    private String preferredVehicleType;
    private String driverPreference;
    private Instant createdAt;

    public boolean hasRole(RoleType role) {
        return roles != null && roles.contains(role);
    }

    public boolean hasRole(String roleName) {
        if (roles == null || roleName == null) return false;
        return roles.stream().anyMatch(r -> r.name().equalsIgnoreCase(roleName)
                || ("ROLE_" + r.name()).equalsIgnoreCase(roleName)
                || r.name().equalsIgnoreCase("ROLE_" + roleName));
    }

    public boolean isStaff() {
        return hasRole(RoleType.ROLE_ADMIN) || hasRole(RoleType.ROLE_FLEET_MANAGER);
    }

    public boolean isAdmin() {
        return hasRole(RoleType.ROLE_ADMIN);
    }
}
