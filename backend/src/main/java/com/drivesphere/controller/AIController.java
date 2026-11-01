package com.drivesphere.controller;

import com.drivesphere.dto.*;
import com.drivesphere.service.AIService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.drivesphere.security.LoginRateLimiter;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AIController {

    private final AIService aiService;
    private final LoginRateLimiter rateLimiter;

    private String getClientIdentifier(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }

    @PostMapping("/recommend-vehicles")
    public ResponseEntity<ApiResponse<AiRecommendationResponse>> recommendVehicles(
            @RequestBody AiRecommendationRequest request,
            HttpServletRequest servletRequest) {
        String client = getClientIdentifier(servletRequest);
        if (rateLimiter.isActionRateLimited("AI_RECOMMEND", client, 30, 60000L)) {
            throw new IllegalStateException("AI recommendation rate limit exceeded. Please wait a moment before trying again.");
        }
        return ResponseEntity.ok(ApiResponse.success(
                aiService.recommendVehicles(request),
                "AI vehicle recommendations generated from DriveSphere fleet"
        ));
    }

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<AiChatResponse>> chat(
            @Valid @RequestBody AiChatRequest request,
            HttpServletRequest servletRequest) {
        if (request.getMessage() != null && request.getMessage().length() > 1000) {
            throw new IllegalArgumentException("Chat prompt exceeds maximum length of 1000 characters.");
        }

        String client = getClientIdentifier(servletRequest);
        if (rateLimiter.isActionRateLimited("AI_CHAT", client, 25, 60000L)) {
            throw new IllegalStateException("AI chat query rate limit exceeded. Please wait a minute before sending additional questions.");
        }

        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean isAuth = auth != null && auth.isAuthenticated() && !auth.getName().equalsIgnoreCase("anonymousUser");
        request.setAuthenticated(isAuth);

        return ResponseEntity.ok(ApiResponse.success(
                aiService.chat(request),
                "AI concierge response"
        ));
    }

    @PostMapping("/trip-assistant")
    public ResponseEntity<ApiResponse<TripPlannerResponse>> planTrip(
            @Valid @RequestBody TripPlannerRequest request,
            HttpServletRequest servletRequest) {
        String client = getClientIdentifier(servletRequest);
        if (rateLimiter.isActionRateLimited("AI_TRIP", client, 20, 60000L)) {
            throw new IllegalStateException("Trip assistant request rate limit exceeded. Please wait a moment.");
        }
        return ResponseEntity.ok(ApiResponse.success(
                aiService.planTrip(request),
                "Smart trip route, fuel cost, and weather calculated"
        ));
    }
}
