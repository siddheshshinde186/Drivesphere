package com.drivesphere.controller;

import com.drivesphere.dto.ApiResponse;
import com.drivesphere.dto.ReviewCreateRequest;
import com.drivesphere.dto.ReviewDto;
import com.drivesphere.dto.UserDto;
import com.drivesphere.service.AuthService;
import com.drivesphere.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final AuthService authService;

    @PostMapping
    public ResponseEntity<ApiResponse<ReviewDto>> submitReview(@Valid @RequestBody ReviewCreateRequest request) {
        UserDto currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Unauthorized"));
        }

        ReviewDto review = reviewService.submitReview(currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(review, "Review submitted successfully. Thank you for your feedback!"));
    }

    @GetMapping("/vehicle/{vehicleId}")
    public ResponseEntity<ApiResponse<List<ReviewDto>>> getVehicleReviews(@PathVariable Long vehicleId) {
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.getVehicleReviews(vehicleId),
                "Vehicle reviews"
        ));
    }

    @GetMapping("/driver/{driverId}")
    public ResponseEntity<ApiResponse<List<ReviewDto>>> getDriverReviews(@PathVariable Long driverId) {
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.getDriverReviews(driverId),
                "Driver reviews"
        ));
    }
}
