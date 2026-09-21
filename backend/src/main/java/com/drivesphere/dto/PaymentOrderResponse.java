package com.drivesphere.dto;

import com.drivesphere.entity.PaymentGateway;
import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentOrderResponse {

    private String orderId;
    private BigDecimal amount;
    private String currency;
    private String keyId;
    private String bookingReference;
    private PaymentGateway gateway;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private boolean testMode;
}
