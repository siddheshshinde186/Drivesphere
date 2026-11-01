package com.drivesphere.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @Column(name = "rating", precision = 3, scale = 2, nullable = false)
    private BigDecimal rating;

    @Column(name = "vehicle_rating", precision = 3, scale = 2, nullable = false)
    private BigDecimal vehicleRating;

    @Column(name = "condition_rating", precision = 3, scale = 2)
    private BigDecimal conditionRating;

    @Column(name = "cleanliness_rating", precision = 3, scale = 2)
    private BigDecimal cleanlinessRating;

    @Column(name = "comfort_rating", precision = 3, scale = 2)
    private BigDecimal comfortRating;

    @Column(name = "driver_rating", precision = 3, scale = 2)
    private BigDecimal driverRating;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;
}
