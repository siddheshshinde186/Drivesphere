package com.drivesphere.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

    private String token;
    @Builder.Default
    private String tokenType = "Bearer";
    private UserDto user;
}
