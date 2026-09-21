package com.drivesphere.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentVerifyRequest {

    @NotNull(message = "Booking ID is required")
    private Long bookingId;

    private String orderId;
    private String paymentId;
    private String signature;

    @Builder.Default
    private boolean mockApproval = false;
}
