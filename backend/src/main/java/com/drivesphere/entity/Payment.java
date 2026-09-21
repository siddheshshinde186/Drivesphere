package com.drivesphere.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;

    @Builder.Default
    @Column(name = "currency", length = 10, nullable = false)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "gateway", length = 30, nullable = false)
    private PaymentGateway gateway = PaymentGateway.MOCK;

    @Column(name = "gateway_order_id", length = 100)
    private String gatewayOrderId;

    @Column(name = "gateway_payment_id", length = 100)
    private String gatewayPaymentId;

    @Column(name = "gateway_signature", length = 255)
    private String gatewaySignature;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", length = 30, nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;
}
