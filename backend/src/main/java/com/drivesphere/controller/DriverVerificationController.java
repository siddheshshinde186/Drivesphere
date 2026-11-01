package com.drivesphere.controller;

import com.drivesphere.dto.ApiResponse;
import com.drivesphere.dto.DriverVerificationDto;
import com.drivesphere.dto.DriverVerificationReviewRequest;
import com.drivesphere.dto.UserDto;
import com.drivesphere.entity.VerificationStatus;
import com.drivesphere.service.AuthService;
import com.drivesphere.service.DriverVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/verifications")
@RequiredArgsConstructor
public class DriverVerificationController {

    private final DriverVerificationService verificationService;
    private final AuthService authService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<DriverVerificationDto>>> getAllVerifications(
            @RequestParam(required = false) VerificationStatus status) {
        return ResponseEntity.ok(ApiResponse.success(
                verificationService.getAllVerifications(status),
                "Driver verification queue"
        ));
    }

    @GetMapping("/driver/{driverId}")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')")
    public ResponseEntity<ApiResponse<DriverVerificationDto>> getVerificationByDriverId(@PathVariable Long driverId) {
        UserDto currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Unauthorized"));
        }
        boolean isAdmin = currentUser.isAdmin();

        return ResponseEntity.ok(ApiResponse.success(
                verificationService.getVerificationByDriverId(driverId, currentUser.getId(), isAdmin),
                "Driver verification details"
        ));
    }

    @PostMapping("/driver/{driverId}")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')")
    public ResponseEntity<ApiResponse<DriverVerificationDto>> submitDocuments(
            @PathVariable Long driverId,
            @RequestBody DriverVerificationDto dto) {
        UserDto currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Unauthorized"));
        }
        boolean isAdmin = currentUser.isAdmin();

        return ResponseEntity.ok(ApiResponse.success(
                verificationService.submitVerificationDocuments(driverId, dto, currentUser.getId(), isAdmin),
                "Verification documents submitted for review"
        ));
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DriverVerificationDto>> reviewVerification(
            @PathVariable Long id,
            @Valid @RequestBody DriverVerificationReviewRequest request) {
        UserDto admin = authService.getCurrentUser();
        Long adminId = admin != null ? admin.getId() : null;

        return ResponseEntity.ok(ApiResponse.success(
                verificationService.reviewVerification(id, adminId, request),
                "Verification decision processed successfully"
        ));
    }
}
