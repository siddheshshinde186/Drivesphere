package com.drivesphere.dto;

import com.drivesphere.entity.RoleType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    private String phoneNumber;

    @Builder.Default
    private RoleType role = RoleType.ROLE_CUSTOMER;

    // Driver-specific fields (optional when registering as driver)
    private String licenseNumber;
    private LocalDate licenseExpiryDate;
    private Integer experienceYears;
    private String address;
    private String languages;
}
