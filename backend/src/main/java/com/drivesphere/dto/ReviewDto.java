package com.drivesphere.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewDto {

    private Long id;
    private Long bookingId;
    private String bookingReference;
    private Long customerId;
    private String customerName;
    private String customerEmail;
    private String bookingStatus;
    private Long vehicleId;
    private String vehicleName;
    private Long driverId;
    private String driverName;
    private BigDecimal rating;
    private BigDecimal vehicleRating;
    private BigDecimal conditionRating;
    private BigDecimal cleanlinessRating;
    private BigDecimal comfortRating;
    private BigDecimal driverRating;
    private String comment;
    private Instant createdAt;
}
