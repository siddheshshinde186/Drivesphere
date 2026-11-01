package com.drivesphere.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

    private String token;
    private String refreshToken;
    private Long expiresIn;
    @Builder.Default
    private String tokenType = "Bearer";
    private UserDto user;

    @JsonProperty("accessToken")
    public String getAccessToken() {
        return token;
    }
}

