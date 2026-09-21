package com.drivesphere.dto;

import com.drivesphere.entity.NotificationType;
import lombok.*;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDto {

    private Long id;
    private String title;
    private String message;
    private NotificationType type;
    private boolean read;
    private String referenceType;
    private Long referenceId;
    private Instant createdAt;
}
