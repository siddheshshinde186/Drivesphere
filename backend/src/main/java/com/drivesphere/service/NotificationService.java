package com.drivesphere.service;

import com.drivesphere.dto.NotificationDto;
import com.drivesphere.entity.Notification;
import com.drivesphere.entity.NotificationType;
import com.drivesphere.entity.User;
import com.drivesphere.exception.ResourceNotFoundException;
import com.drivesphere.mapper.EntityDtoMapper;
import com.drivesphere.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final EntityDtoMapper mapper;
    private final EmailService emailService;

    @Autowired
    public NotificationService(NotificationRepository notificationRepository,
                               EntityDtoMapper mapper,
                               EmailService emailService) {
        this.notificationRepository = notificationRepository;
        this.mapper = mapper;
        this.emailService = emailService;
    }

    public NotificationService(NotificationRepository notificationRepository, EntityDtoMapper mapper) {
        this(notificationRepository, mapper, null);
    }

    @Transactional
    public NotificationDto createNotification(User user, String title, String message,
                                             NotificationType type, String refType, Long refId) {
        Notification notification = Notification.builder()
                .user(user)
                .title(title)
                .message(message)
                .type(type != null ? type : NotificationType.ALERT)
                .read(false)
                .referenceType(refType)
                .referenceId(refId)
                .build();

        Notification saved = notificationRepository.save(notification);

        if (user != null && user.getEmail() != null && !user.getEmail().isBlank()) {
            try {
                if (emailService != null) {
                    emailService.sendNotificationEmail(user.getEmail(), title, message);
                }
            } catch (Exception e) {
                log.warn("Failed to dispatch email notification to {}: {}", user.getEmail(), e.getMessage());
            }
        }

        return mapper.toNotificationDto(saved);
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> getUserNotifications(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(mapper::toNotificationDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + notificationId));

        if (notification.getUser() == null || !notification.getUser().getId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Unauthorized: You cannot access or modify this notification.");
        }

        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        List<Notification> unread = notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId);
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
    }
}
