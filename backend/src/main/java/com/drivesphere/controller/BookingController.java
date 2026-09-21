package com.drivesphere.controller;

import com.drivesphere.dto.ApiResponse;
import com.drivesphere.dto.BookingCreateRequest;
import com.drivesphere.dto.BookingDto;
import com.drivesphere.dto.RentalActionRequest;
import com.drivesphere.dto.UserDto;
import com.drivesphere.service.AuthService;
import com.drivesphere.service.BookingService;
import com.drivesphere.service.RentalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final RentalService rentalService;
    private final AuthService authService;

    @PostMapping
    public ResponseEntity<ApiResponse<BookingDto>> createBooking(@Valid @RequestBody BookingCreateRequest request) {
        UserDto currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Unauthorized"));
        }

        BookingDto booking = bookingService.createBooking(currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(booking, "Booking initiated successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BookingDto>> getBookingById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.getBookingById(id), "Booking details"));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<BookingDto>>> getMyBookings() {
        UserDto currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Unauthorized"));
        }
        return ResponseEntity.ok(ApiResponse.success(
                bookingService.getCustomerBookings(currentUser.getId()),
                "My bookings"
        ));
    }

    @GetMapping("/driver/{driverId}")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN', 'FLEET_MANAGER')")
    public ResponseEntity<ApiResponse<List<BookingDto>>> getDriverBookings(@PathVariable Long driverId) {
        return ResponseEntity.ok(ApiResponse.success(
                bookingService.getDriverBookings(driverId),
                "Driver assigned bookings"
        ));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FLEET_MANAGER')")
    public ResponseEntity<ApiResponse<List<BookingDto>>> getAllBookings() {
        return ResponseEntity.ok(ApiResponse.success(bookingService.getAllBookings(), "All platform bookings"));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<BookingDto>> cancelBooking(@PathVariable Long id) {
        UserDto currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Unauthorized"));
        }

        return ResponseEntity.ok(ApiResponse.success(
                bookingService.cancelBooking(id, currentUser.getId()),
                "Booking cancelled"
        ));
    }

    @PostMapping("/{id}/pickup")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'ADMIN', 'DRIVER')")
    public ResponseEntity<ApiResponse<BookingDto>> pickupVehicle(
            @PathVariable Long id,
            @RequestBody(required = false) RentalActionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                rentalService.pickupVehicle(id, request),
                "Vehicle pickup recorded. Rental is now active."
        ));
    }

    @PostMapping("/{id}/return")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'ADMIN', 'DRIVER')")
    public ResponseEntity<ApiResponse<BookingDto>> returnVehicle(
            @PathVariable Long id,
            @RequestBody(required = false) RentalActionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                rentalService.returnVehicle(id, request),
                "Vehicle returned. Rental completed."
        ));
    }
}
