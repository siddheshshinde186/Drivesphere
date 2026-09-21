package com.drivesphere.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentOrderRequest {

    @NotNull(message = "Booking ID is required")
    private Long bookingId;

    @Builder.Default
    private String paymentMethod = "CARD";
}
