package com.drivesphere.controller;

import com.drivesphere.dto.ApiResponse;
import com.drivesphere.dto.DriverDto;
import com.drivesphere.dto.ReviewDto;
import com.drivesphere.dto.UserDto;
import com.drivesphere.entity.*;
import com.drivesphere.repository.*;
import com.drivesphere.service.AuditLogService;
import com.drivesphere.service.DriverService;
import com.drivesphere.service.ReviewService;
import com.drivesphere.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserService userService;
    private final AuditLogService auditLogService;
    private final ReviewService reviewService;
    private final DriverService driverService;
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final ReviewRepository reviewRepository;

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<UserDto>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponse.success(userService.getAllUsers(), "Registered users retrieved"));
    }

    @PutMapping("/users/{id}/toggle-status")
    public ResponseEntity<ApiResponse<UserDto>> toggleUserStatus(@PathVariable Long id) {
        UserDto user = userService.toggleUserActiveStatus(id);
        auditLogService.logAction("TOGGLE_USER_STATUS", "USER", String.valueOf(id), "Active set to: " + user.isActive(), null);
        return ResponseEntity.ok(ApiResponse.success(user, "User active status updated"));
    }

    @PostMapping("/users/{id}/roles")
    public ResponseEntity<ApiResponse<UserDto>> assignRole(@PathVariable Long id, @RequestParam RoleType role) {
        UserDto user = userService.assignRole(id, role);
        auditLogService.logAction("ASSIGN_ROLE", "USER", String.valueOf(id), "Role assigned: " + role, null);
        return ResponseEntity.ok(ApiResponse.success(user, "Role assigned successfully"));
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<List<AuditLog>>> getAuditLogs() {
        return ResponseEntity.ok(ApiResponse.success(auditLogService.getRecentAuditLogs(), "Audit logs retrieved"));
    }

    // --- Reviews & Moderation ---
    @GetMapping("/reviews")
    public ResponseEntity<ApiResponse<List<ReviewDto>>> getAllReviews() {
        return ResponseEntity.ok(ApiResponse.success(reviewService.getAllReviewsAdmin(), "All platform reviews retrieved"));
    }

    @GetMapping("/reviews/{id}")
    public ResponseEntity<ApiResponse<ReviewDto>> getReviewById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.getReviewById(id), "Review details"));
    }

    @DeleteMapping("/reviews/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteReview(@PathVariable Long id) {
        reviewService.deleteReview(id);
        auditLogService.logAction("DELETE_REVIEW", "REVIEW", String.valueOf(id), "Admin deleted review ID " + id, null);
        return ResponseEntity.ok(ApiResponse.success(null, "Review deleted and ratings recalculated successfully"));
    }

    // --- Driver Presence Telemetry ---
    @GetMapping("/drivers/presence")
    public ResponseEntity<ApiResponse<List<DriverDto>>> getDriverPresence() {
        List<DriverDto> drivers = driverService.getAllDrivers();
        return ResponseEntity.ok(ApiResponse.success(drivers, "Driver presence roster retrieved"));
    }

    // --- Comprehensive Real-Data Platform Summary ---
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPlatformSummary() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(120);

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalUsers", userRepository.count());
        summary.put("activeUsers", userRepository.countByActiveTrue());

        summary.put("totalDrivers", driverRepository.count());
        summary.put("verifiedDrivers", driverRepository.countByVerificationStatus(VerificationStatus.APPROVED));
        summary.put("onlineDrivers", driverRepository.countOnlineDrivers(threshold));
        summary.put("busyDrivers", driverRepository.countBusyDrivers());

        summary.put("totalVehicles", vehicleRepository.count());
        summary.put("availableVehicles", vehicleRepository.countByStatus(VehicleStatus.AVAILABLE));
        summary.put("maintenanceVehicles", vehicleRepository.countByStatus(VehicleStatus.MAINTENANCE));

        summary.put("totalBookings", bookingRepository.count());
        summary.put("activeBookings", bookingRepository.countByBookingStatus(BookingStatus.ACTIVE));
        summary.put("completedBookings", bookingRepository.countByBookingStatus(BookingStatus.COMPLETED));
        summary.put("cancelledBookings", bookingRepository.countByBookingStatus(BookingStatus.CANCELLED));

        BigDecimal totalRev = bookingRepository.sumTotalPaidRevenue();
        summary.put("totalPaidRevenue", totalRev != null ? totalRev : BigDecimal.ZERO);
        summary.put("pendingPayments", paymentRepository.countByStatus(PaymentStatus.PENDING));

        summary.put("totalReviews", reviewRepository.count());
        BigDecimal avgRating = reviewRepository.findOverallAverageRating();
        summary.put("averageRating", avgRating != null ? avgRating.setScale(2, RoundingMode.HALF_UP) : BigDecimal.valueOf(5.00));

        return ResponseEntity.ok(ApiResponse.success(summary, "Authoritative platform summary retrieved"));
    }
}
