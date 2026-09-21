package com.drivesphere.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiChatRequest {

    @NotBlank(message = "Message cannot be empty")
    private String message;

    private String conversationContext;

    private Boolean authenticated;
}
