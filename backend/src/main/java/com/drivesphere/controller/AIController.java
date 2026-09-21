package com.drivesphere.controller;

import com.drivesphere.dto.*;
import com.drivesphere.service.AIService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AIController {

    private final AIService aiService;

    @PostMapping("/recommend-vehicles")
    public ResponseEntity<ApiResponse<AiRecommendationResponse>> recommendVehicles(
            @RequestBody AiRecommendationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                aiService.recommendVehicles(request),
                "AI vehicle recommendations generated from DriveSphere fleet"
        ));
    }

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<AiChatResponse>> chat(@Valid @RequestBody AiChatRequest request) {
        if (request.getAuthenticated() == null) {
            org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !auth.getName().equalsIgnoreCase("anonymousUser")) {
                request.setAuthenticated(true);
            } else {
                request.setAuthenticated(false);
            }
        }

        return ResponseEntity.ok(ApiResponse.success(
                aiService.chat(request),
                "AI concierge response"
        ));
    }

    @PostMapping("/trip-assistant")
    public ResponseEntity<ApiResponse<TripPlannerResponse>> planTrip(
            @Valid @RequestBody TripPlannerRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                aiService.planTrip(request),
                "Smart trip route, fuel cost, and weather calculated"
        ));
    }
}
