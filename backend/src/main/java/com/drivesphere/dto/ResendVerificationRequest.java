package com.drivesphere.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResendVerificationRequest {

    @JsonAlias({"identifier", "username"})
    private String email;

    @AssertTrue(message = "Please provide a valid email address or phone number")
    public boolean isValidIdentifier() {
        if (email == null || email.isBlank()) {
            return false;
        }
        String trimmed = email.trim();
        return trimmed.contains("@") || trimmed.matches("^[+]?[0-9\\s\\-()]{7,20}$");
    }
}
