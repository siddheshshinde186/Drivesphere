package com.drivesphere.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "vehicle_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleImage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "image_url", length = 500, nullable = false)
    private String imageUrl;

    @Builder.Default
    @Column(name = "is_primary", nullable = false)
    private boolean primary = false;
}
