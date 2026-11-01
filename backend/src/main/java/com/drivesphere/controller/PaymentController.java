package com.drivesphere.controller;

import com.drivesphere.dto.ApiResponse;
import com.drivesphere.dto.PaymentOrderRequest;
import com.drivesphere.dto.PaymentOrderResponse;
import com.drivesphere.dto.PaymentVerifyRequest;
import com.drivesphere.dto.UserDto;
import com.drivesphere.service.AuthService;
import com.drivesphere.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final AuthService authService;

    @PostMapping("/create-order")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PaymentOrderResponse>> createOrder(@Valid @RequestBody PaymentOrderRequest request) {
        UserDto currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Unauthorized"));
        }
        boolean isStaff = currentUser.isStaff();

        PaymentOrderResponse response = paymentService.createPaymentOrder(request.getBookingId(), currentUser.getId(), isStaff);
        return ResponseEntity.ok(ApiResponse.success(response, "Payment order generated"));
    }

    @PostMapping("/verify")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Boolean>> verifyPayment(@Valid @RequestBody PaymentVerifyRequest request) {
        UserDto currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Unauthorized"));
        }
        boolean isStaff = currentUser.isStaff();

        boolean verified = paymentService.verifyPayment(request, currentUser.getId(), isStaff);
        if (verified) {
            return ResponseEntity.ok(ApiResponse.success(true, "Payment verified successfully. Booking confirmed!"));
        } else {
            return ResponseEntity.badRequest().body(ApiResponse.error("Payment verification failed. Invalid signature."));
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleRazorpayWebhook(
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestBody String rawPayload) {
        boolean handled = paymentService.handleWebhook(rawPayload, signature);
        if (handled) {
            return ResponseEntity.ok("OK");
        }
        return ResponseEntity.badRequest().body("Signature verification failed or payload error");
    }
}
