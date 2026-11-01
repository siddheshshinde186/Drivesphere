package com.drivesphere.controller;

import com.drivesphere.dto.*;
import com.drivesphere.service.AuthService;
import com.drivesphere.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.drivesphere.service.DocumentStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final AuthService authService;
    private final DocumentStorageService documentStorageService;

    @Autowired
    public UserController(UserService userService, AuthService authService, DocumentStorageService documentStorageService) {
        this.userService = userService;
        this.authService = authService;
        this.documentStorageService = documentStorageService;
    }

    public UserController(UserService userService, AuthService authService) {
        this(userService, authService, null);
    }

    /**
     * Retrieve authenticated customer's profile.
     * Derived strictly from validated JWT security context.
     */
    @GetMapping({"/profile", "/me"})
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<UserDto>> getMyProfile() {
        UserDto currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized: Please sign in to view your profile."));
        }
        return ResponseEntity.ok(ApiResponse.success(currentUser));
    }

    /**
     * Update customer profile personal details.
     * Prevents modifying sensitive security properties (roles, active, verified, email).
     */
    @PutMapping({"/profile", "/me"})
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserDto>> updateMyProfile(@Valid @RequestBody UserProfileUpdateRequest request) {
        UserDto currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized: Please sign in to update your profile."));
        }

        log.info("Updating profile for customer ID {}: {} {}", currentUser.getId(), request.getFirstName(), request.getLastName());
        UserDto updated = userService.updateUserProfile(currentUser.getId(), request);
        return ResponseEntity.ok(ApiResponse.success(updated, "Profile updated successfully."));
    }

    /**
     * Upload customer profile avatar image.
     * Enforces JPEG, PNG, WEBP format, max 5MB size limit, and path traversal protection.
     */
    @PostMapping(value = "/profile/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserDto>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        UserDto currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized: Please sign in."));
        }

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Avatar image file is required."));
        }

        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equalsIgnoreCase("image/jpeg")
                && !contentType.equalsIgnoreCase("image/png")
                && !contentType.equalsIgnoreCase("image/webp"))) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Only JPEG, PNG, and WEBP image formats are supported for avatars."));
        }

        if (documentStorageService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Document storage service is not configured."));
        }

        StoredDocumentDto stored = documentStorageService.storePublicFile(file, currentUser.getId());
        if (stored == null) {
            stored = documentStorageService.storeFile(file, currentUser.getId());
        }
        UserDto updated = userService.updateAvatar(currentUser.getId(), stored.getFileUrl());
        return ResponseEntity.ok(ApiResponse.success(updated, "Profile avatar updated successfully."));
    }

    /**
     * Customer Activity / Booking History Statistics.
     */
    @GetMapping("/stats")
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<UserStatsDto>> getMyStats() {
        UserDto currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized: Please sign in."));
        }

        UserStatsDto stats = userService.getUserStats(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    /**
     * Change Password for authenticated customer.
     */
    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        UserDto currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized: Please sign in."));
        }

        authService.changePassword(currentUser.getId(), request);
        return ResponseEntity.ok(ApiResponse.success(null, "Password updated successfully."));
    }
}

