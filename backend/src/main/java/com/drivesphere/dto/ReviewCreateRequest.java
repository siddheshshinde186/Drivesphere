package com.drivesphere.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewCreateRequest {

    @NotNull(message = "Booking ID is required")
    private Long bookingId;

    @NotNull(message = "Overall rating is required")
    @DecimalMin(value = "1.0", message = "Rating must be between 1 and 5")
    @DecimalMax(value = "5.0", message = "Rating must be between 1 and 5")
    private BigDecimal rating;

    @NotNull(message = "Vehicle rating is required")
    @DecimalMin(value = "1.0", message = "Rating must be between 1 and 5")
    @DecimalMax(value = "5.0", message = "Rating must be between 1 and 5")
    private BigDecimal vehicleRating;

    @DecimalMin(value = "1.0", message = "Rating must be between 1 and 5")
    @DecimalMax(value = "5.0", message = "Rating must be between 1 and 5")
    private BigDecimal driverRating;

    @NotBlank(message = "Review comment is required")
    private String comment;
}
