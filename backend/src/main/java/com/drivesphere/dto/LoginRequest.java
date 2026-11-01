package com.drivesphere.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Login request payload supporting dual authentication via registered email or phone number.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginRequest {

    /**
     * Primary generic login identifier: registered email address or phone number.
     */
    @JsonAlias({"username", "user"})
    private String identifier;

    /**
     * Backward-compatible email field for legacy clients.
     */
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    /**
     * Resolves the effective login identifier from either identifier or legacy email.
     */
    public String getEffectiveIdentifier() {
        if (identifier != null && !identifier.trim().isBlank()) {
            return identifier.trim();
        }
        if (email != null && !email.trim().isBlank()) {
            return email.trim();
        }
        return "";
    }

    @AssertTrue(message = "Email address or phone number is required")
    public boolean isValidIdentifier() {
        return !getEffectiveIdentifier().isBlank();
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
        if (this.email == null || this.email.isBlank()) {
            this.email = identifier;
        }
    }

    public void setEmail(String email) {
        this.email = email;
        if (this.identifier == null || this.identifier.isBlank()) {
            this.identifier = email;
        }
    }
}
