package com.drivesphere.dto;

import lombok.*;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiChatResponse {

    private String reply;
    private List<String> suggestedQuestions;
    private boolean fallbackMode;
    private String modelProvider;
    @Builder.Default
    private Instant timestamp = Instant.now();
}
