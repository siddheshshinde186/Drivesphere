package com.drivesphere.dto;

import com.drivesphere.entity.BookingStatus;
import com.drivesphere.entity.PaymentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingDto {

    private Long id;
    private String bookingReference;
    private UserDto customer;
    private VehicleDto vehicle;
    private DriverDto driver;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private String pickupLocation;
    private String dropLocation;
    private BigDecimal pickupLatitude;
    private BigDecimal pickupLongitude;
    private BigDecimal dropLatitude;
    private BigDecimal dropLongitude;
    private Integer totalDays;
    private Integer totalHours;
    private BigDecimal rentalAmount;
    private BigDecimal driverAmount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private BookingStatus bookingStatus;
    private PaymentStatus paymentStatus;
    private LocalDateTime pickupTime;
    private LocalDateTime returnTime;
    private String notes;
    private Long tripId;
    private boolean pickupEligible;
    private Instant createdAt;
}
