package com.drivesphere.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_actor", columnList = "actor_email"),
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_resource", columnList = "resource_type, resource_id"),
        @Index(name = "idx_audit_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog extends BaseEntity {

    @Column(name = "actor_email", length = 120)
    private String actorEmail;

    @Column(name = "actor_role", length = 50)
    private String actorRole;

    @Column(name = "action", length = 80, nullable = false)
    private String action;

    @Column(name = "resource_type", length = 60, nullable = false)
    private String resourceType;

    @Column(name = "resource_id", length = 60)
    private String resourceId;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "ip_address", length = 60)
    private String ipAddress;
}
