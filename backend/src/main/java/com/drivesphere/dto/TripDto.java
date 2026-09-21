package com.drivesphere.dto;

import com.drivesphere.entity.TripStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripDto {

    private Long id;
    private Long bookingId;
    private String bookingReference;
    private Long vehicleId;
    private String vehicleName;
    private BigDecimal startLatitude;
    private BigDecimal startLongitude;
    private BigDecimal destLatitude;
    private BigDecimal destLongitude;
    private BigDecimal currentLatitude;
    private BigDecimal currentLongitude;
    private BigDecimal distanceKm;
    private BigDecimal distanceTraveledKm;
    private BigDecimal remainingDistanceKm;
    private TripStatus status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer simulationStep;
}
