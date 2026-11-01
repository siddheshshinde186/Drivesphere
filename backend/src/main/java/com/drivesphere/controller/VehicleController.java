package com.drivesphere.controller;

import com.drivesphere.dto.ApiResponse;
import com.drivesphere.dto.PublicVehicleDto;
import com.drivesphere.dto.StoredDocumentDto;
import com.drivesphere.dto.UserDto;
import com.drivesphere.dto.VehicleCreateUpdateRequest;
import com.drivesphere.dto.VehicleDto;
import com.drivesphere.entity.FuelType;
import com.drivesphere.entity.Transmission;
import com.drivesphere.entity.VehicleType;
import com.drivesphere.service.AuthService;
import com.drivesphere.service.DocumentStorageService;
import com.drivesphere.service.VehicleService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;
    private final AuthService authService;
    private final DocumentStorageService documentStorageService;

    public VehicleController(VehicleService vehicleService) {
        this(vehicleService, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public VehicleController(VehicleService vehicleService, AuthService authService, DocumentStorageService documentStorageService) {
        this.vehicleService = vehicleService;
        this.authService = authService;
        this.documentStorageService = documentStorageService;
    }

    /**
     * Complete fleet inventory for authorized administrators and fleet managers.
     * Contains internal operational properties (registrationNumber, vin, odometer, coordinates).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FLEET_MANAGER')")
    public ResponseEntity<ApiResponse<List<VehicleDto>>> getAllVehicles() {
        return ResponseEntity.ok(ApiResponse.success(vehicleService.getAllVehicles(), "Vehicles retrieved"));
    }

    /**
     * Public Authoritative Operational Fleet.
     * Excludes non-operational vehicles and sensitive internal properties.
     */
    @GetMapping("/available")
    public ResponseEntity<ApiResponse<List<PublicVehicleDto>>> getAvailableVehicles() {
        return ResponseEntity.ok(ApiResponse.success(vehicleService.getAvailableVehicles(), "Available vehicles retrieved"));
    }

    /**
     * Public Customer-Visible Explore Fleet.
     * Displays all registered vehicles eligible for customer viewing (excluding only INACTIVE vehicles).
     * Distinguishes operational status (AVAILABLE, BOOKED, RENTED, MAINTENANCE).
     */
    @GetMapping({"/fleet", "/explore"})
    public ResponseEntity<ApiResponse<List<PublicVehicleDto>>> getCustomerFleet() {
        return ResponseEntity.ok(ApiResponse.success(vehicleService.getCustomerVisibleFleet(), "Customer fleet retrieved"));
    }

    /**
     * Public Date-Specific Operational Fleet.
     */
    @GetMapping("/available-dates")
    public ResponseEntity<ApiResponse<List<PublicVehicleDto>>> getAvailableVehiclesForDates(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        if (start == null || end == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Both start and end date-time must be provided."));
        }
        if (end.isBefore(start) || end.isEqual(start)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("End date-time must be strictly after start date-time."));
        }
        return ResponseEntity.ok(ApiResponse.success(
                vehicleService.getAvailableVehiclesForPeriod(start, end),
                "Available vehicles for period retrieved"
        ));
    }

    /**
     * Public Multi-Criteria Vehicle Filtering.
     */
    @GetMapping("/filter")
    public ResponseEntity<ApiResponse<List<PublicVehicleDto>>> filterVehicles(
            @RequestParam(required = false) VehicleType type,
            @RequestParam(required = false) FuelType fuel,
            @RequestParam(required = false) Transmission transmission,
            @RequestParam(required = false) Integer minSeats,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDateTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDateTime) {
        
        if ((startDateTime != null && endDateTime == null) || (startDateTime == null && endDateTime != null)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Both startDateTime and endDateTime must be provided for rental period filtering."));
        }
        if (startDateTime != null && endDateTime != null) {
            if (endDateTime.isBefore(startDateTime) || endDateTime.isEqual(startDateTime)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("endDateTime must be strictly after startDateTime."));
            }
        }

        return ResponseEntity.ok(ApiResponse.success(
                vehicleService.filterVehicles(type, fuel, transmission, minSeats, maxPrice, brand, startDateTime, endDateTime),
                "Filtered vehicles retrieved"
        ));
    }

    /**
     * Public Safe Vehicle Details for customer booking modal and detail viewing.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PublicVehicleDto>> getVehicleById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(vehicleService.getPublicVehicleById(id), "Vehicle details"));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FLEET_MANAGER')")
    public ResponseEntity<ApiResponse<VehicleDto>> createVehicle(@Valid @RequestBody VehicleCreateUpdateRequest request) {
        VehicleDto created = vehicleService.createVehicle(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Vehicle created successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FLEET_MANAGER')")
    public ResponseEntity<ApiResponse<VehicleDto>> updateVehicle(@PathVariable Long id,
                                                                @Valid @RequestBody VehicleCreateUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(vehicleService.updateVehicle(id, request), "Vehicle updated"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FLEET_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> deleteVehicle(@PathVariable Long id) {
        vehicleService.deleteVehicle(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Vehicle deactivated successfully"));
    }

    @PutMapping("/{id}/toggle-status")
    @PreAuthorize("hasAnyRole('ADMIN', 'FLEET_MANAGER')")
    public ResponseEntity<ApiResponse<VehicleDto>> toggleVehicleStatus(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(vehicleService.toggleVehicleStatus(id), "Vehicle operational status updated"));
    }

    /**
     * Upload vehicle image (primary or gallery).
     * Automatically uploads to Cloudinary (when configured) or secure local storage.
     */
    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'FLEET_MANAGER')")
    public ResponseEntity<ApiResponse<VehicleDto>> uploadVehicleImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "isPrimary", defaultValue = "true") boolean isPrimary) {
        if (authService != null) {
            UserDto currentUser = authService.getCurrentUser();
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Unauthorized"));
            }
        }

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Image file is required."));
        }

        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equalsIgnoreCase("image/jpeg")
                && !contentType.equalsIgnoreCase("image/png")
                && !contentType.equalsIgnoreCase("image/webp"))) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Only JPEG, PNG, and WEBP image formats are supported for vehicles."));
        }

        if (documentStorageService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error("Document storage service unavailable"));
        }

        StoredDocumentDto stored = documentStorageService.storePublicFile(file, null);
        VehicleDto updated = vehicleService.addVehicleImage(id, stored.getFileUrl(), isPrimary);
        return ResponseEntity.ok(ApiResponse.success(updated, "Vehicle image uploaded successfully."));
    }
}
