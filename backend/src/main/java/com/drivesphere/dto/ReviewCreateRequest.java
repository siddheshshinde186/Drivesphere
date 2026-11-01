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

    @DecimalMin(value = "1.0", message = "Vehicle rating must be between 1 and 5")
    @DecimalMax(value = "5.0", message = "Vehicle rating must be between 1 and 5")
    private BigDecimal vehicleRating;

    @DecimalMin(value = "1.0", message = "Condition rating must be between 1 and 5")
    @DecimalMax(value = "5.0", message = "Condition rating must be between 1 and 5")
    private BigDecimal conditionRating;

    @DecimalMin(value = "1.0", message = "Cleanliness rating must be between 1 and 5")
    @DecimalMax(value = "5.0", message = "Cleanliness rating must be between 1 and 5")
    private BigDecimal cleanlinessRating;

    @DecimalMin(value = "1.0", message = "Comfort rating must be between 1 and 5")
    @DecimalMax(value = "5.0", message = "Comfort rating must be between 1 and 5")
    private BigDecimal comfortRating;

    @DecimalMin(value = "1.0", message = "Driver rating must be between 1 and 5")
    @DecimalMax(value = "5.0", message = "Driver rating must be between 1 and 5")
    private BigDecimal driverRating;

    @jakarta.validation.constraints.Size(max = 2000, message = "Comment must not exceed 2000 characters")
    private String comment;
}
