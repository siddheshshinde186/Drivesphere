package com.drivesphere.controller;

import com.drivesphere.dto.ApiResponse;
import com.drivesphere.dto.PaymentOrderRequest;
import com.drivesphere.dto.PaymentOrderResponse;
import com.drivesphere.dto.PaymentVerifyRequest;
import com.drivesphere.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/create-order")
    public ResponseEntity<ApiResponse<PaymentOrderResponse>> createOrder(@Valid @RequestBody PaymentOrderRequest request) {
        PaymentOrderResponse response = paymentService.createPaymentOrder(request.getBookingId());
        return ResponseEntity.ok(ApiResponse.success(response, "Payment order generated"));
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<Boolean>> verifyPayment(@Valid @RequestBody PaymentVerifyRequest request) {
        boolean verified = paymentService.verifyPayment(request);
        if (verified) {
            return ResponseEntity.ok(ApiResponse.success(true, "Payment verified successfully. Booking confirmed!"));
        } else {
            return ResponseEntity.badRequest().body(ApiResponse.error("Payment verification failed. Invalid signature."));
        }
    }
}
