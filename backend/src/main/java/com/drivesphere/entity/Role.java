package com.drivesphere.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "name", length = 50, nullable = false, unique = true)
    private RoleType name;

    @Column(name = "description", length = 255)
    private String description;
}
