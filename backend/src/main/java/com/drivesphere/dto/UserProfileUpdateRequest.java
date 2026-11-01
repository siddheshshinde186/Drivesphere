package com.drivesphere.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileUpdateRequest {

    @Size(max = 60, message = "First name must be up to 60 characters")
    private String firstName;

    @Size(max = 60, message = "Last name must be up to 60 characters")
    private String lastName;

    @Pattern(regexp = "^$|^[+0-9\\s-]{8,20}$", message = "Phone number must be valid digits")
    private String phoneNumber;

    @Size(max = 500, message = "Avatar URL is too long")
    private String avatarUrl;

    private String preferredVehicleType;

    private String driverPreference;
}

