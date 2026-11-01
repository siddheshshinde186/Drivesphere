package com.drivesphere.controller;

import com.drivesphere.dto.ApiResponse;
import com.drivesphere.dto.DriverDto;
import com.drivesphere.dto.StoredDocumentDto;
import com.drivesphere.dto.UserDto;
import com.drivesphere.service.AuthService;
import com.drivesphere.service.DocumentStorageService;
import com.drivesphere.service.DriverService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/drivers")
public class DriverController {

    private final DriverService driverService;
    private final AuthService authService;
    private final DocumentStorageService documentStorageService;

    public DriverController(DriverService driverService, AuthService authService) {
        this(driverService, authService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DriverController(DriverService driverService, AuthService authService, DocumentStorageService documentStorageService) {
        this.driverService = driverService;
        this.authService = authService;
        this.documentStorageService = documentStorageService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FLEET_MANAGER')")
    public ResponseEntity<ApiResponse<List<DriverDto>>> getAllDrivers() {
        return ResponseEntity.ok(ApiResponse.success(driverService.getAllDrivers(), "All drivers"));
    }

    @GetMapping("/available")
    public ResponseEntity<ApiResponse<List<DriverDto>>> getAvailableDrivers() {
        return ResponseEntity.ok(ApiResponse.success(
                driverService.getAvailableAndApprovedDrivers(),
                "Available and verified drivers for customer hiring"
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DriverDto>> getDriverById(@PathVariable Long id) {
        UserDto currentUser = authService.getCurrentUser();
        Long userId = currentUser != null ? currentUser.getId() : null;
        boolean isStaff = currentUser != null && currentUser.isStaff();

        return ResponseEntity.ok(ApiResponse.success(
                driverService.getDriverById(id, userId, isStaff),
                "Driver profile"
        ));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<DriverDto>> getMyDriverProfile() {
        UserDto currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Unauthorized"));
        }
        return ResponseEntity.ok(ApiResponse.success(
                driverService.getDriverByUserId(currentUser.getId()),
                "My driver profile"
        ));
    }

    @PostMapping("/heartbeat")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<DriverDto>> sendHeartbeat() {
        UserDto currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Unauthorized"));
        }
        return ResponseEntity.ok(ApiResponse.success(
                driverService.updateHeartbeat(currentUser.getId()),
                "Driver heartbeat acknowledged; presence refreshed"
        ));
    }

    @PostMapping("/online-status")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<DriverDto>> updateOnlineStatus(@RequestBody java.util.Map<String, Boolean> payload) {
        UserDto currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Unauthorized"));
        }
        boolean online = Boolean.TRUE.equals(payload.get("online"));
        return ResponseEntity.ok(ApiResponse.success(
                driverService.setOnlineStatus(currentUser.getId(), online),
                online ? "Driver is now ONLINE and available for assignments" : "Driver is now OFFLINE"
        ));
    }

    @PutMapping("/{id}/availability")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN', 'FLEET_MANAGER')")
    public ResponseEntity<ApiResponse<DriverDto>> toggleAvailability(@PathVariable Long id) {
        UserDto currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Unauthorized"));
        }
        boolean isStaff = currentUser.isStaff();

        return ResponseEntity.ok(ApiResponse.success(
                driverService.toggleAvailability(id, currentUser.getId(), isStaff),
                "Driver availability updated"
        ));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')")
    public ResponseEntity<ApiResponse<DriverDto>> updateProfile(@PathVariable Long id, @RequestBody DriverDto dto) {
        UserDto currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Unauthorized"));
        }
        boolean isAdmin = currentUser.isAdmin();

        return ResponseEntity.ok(ApiResponse.success(
                driverService.updateProfile(id, dto, currentUser.getId(), isAdmin),
                "Driver profile updated"
        ));
    }

    /**
     * Upload driver profile photo.
     * Automatically uploads to Cloudinary (when configured) or secure local storage.
     */
    @PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')")
    public ResponseEntity<ApiResponse<DriverDto>> uploadDriverPhoto(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        UserDto currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Unauthorized"));
        }

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Photo file is required."));
        }

        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equalsIgnoreCase("image/jpeg")
                && !contentType.equalsIgnoreCase("image/png")
                && !contentType.equalsIgnoreCase("image/webp"))) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Only JPEG, PNG, and WEBP image formats are supported for driver photos."));
        }

        if (documentStorageService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error("Document storage service unavailable"));
        }

        boolean isAdmin = currentUser.isAdmin();
        StoredDocumentDto stored = documentStorageService.storePublicFile(file, currentUser.getId());
        DriverDto updated = driverService.updateProfilePhoto(id, stored.getFileUrl(), currentUser.getId(), isAdmin);
        return ResponseEntity.ok(ApiResponse.success(updated, "Driver profile photo uploaded successfully."));
    }
}
