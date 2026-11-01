package com.drivesphere.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking extends BaseEntity {

    @Column(name = "booking_reference", length = 32, nullable = false, unique = true)
    private String bookingReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @Column(name = "start_date_time", nullable = false)
    private LocalDateTime startDateTime;

    @Column(name = "end_date_time", nullable = false)
    private LocalDateTime endDateTime;

    @Column(name = "pickup_location", length = 255, nullable = false)
    private String pickupLocation;

    @Column(name = "drop_location", length = 255, nullable = false)
    private String dropLocation;

    @Column(name = "pickup_latitude", precision = 10, scale = 7)
    private BigDecimal pickupLatitude;

    @Column(name = "pickup_longitude", precision = 10, scale = 7)
    private BigDecimal pickupLongitude;

    @Column(name = "drop_latitude", precision = 10, scale = 7)
    private BigDecimal dropLatitude;

    @Column(name = "drop_longitude", precision = 10, scale = 7)
    private BigDecimal dropLongitude;

    @Builder.Default
    @Column(name = "total_days", nullable = false)
    private Integer totalDays = 1;

    @Column(name = "total_hours")
    private Integer totalHours;

    @Column(name = "rental_amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal rentalAmount;

    @Builder.Default
    @Column(name = "driver_amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal driverAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "tax_amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "tax_rate", precision = 5, scale = 4)
    private BigDecimal taxRate = BigDecimal.valueOf(0.18);

    @Builder.Default
    @Column(name = "discount_amount", precision = 10, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "deposit_amount", precision = 10, scale = 2)
    private BigDecimal depositAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "booking_status", length = 30, nullable = false)
    private BookingStatus bookingStatus = BookingStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "payment_status", length = 30, nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(name = "hold_expires_at")
    private LocalDateTime holdExpiresAt;

    @Column(name = "pricing_snapshot", columnDefinition = "TEXT")
    private String pricingSnapshot;

    @Column(name = "pickup_time")
    private LocalDateTime pickupTime;

    @Column(name = "return_time")
    private LocalDateTime returnTime;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}
