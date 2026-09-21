package com.drivesphere.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "vehicle_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleCategory extends BaseEntity {

    @Column(name = "name", length = 60, nullable = false, unique = true)
    private String name;

    @Column(name = "slug", length = 60, nullable = false, unique = true)
    private String slug;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @Column(name = "base_rate_per_km", precision = 10, scale = 2, nullable = false)
    private BigDecimal baseRatePerKm = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "base_fare", precision = 10, scale = 2, nullable = false)
    private BigDecimal baseFare = BigDecimal.ZERO;
}
