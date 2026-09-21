package com.drivesphere.controller;

import com.drivesphere.dto.ApiResponse;
import com.drivesphere.dto.NotificationDto;
import com.drivesphere.dto.UserDto;
import com.drivesphere.service.AuthService;
import com.drivesphere.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final AuthService authService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationDto>>> getMyNotifications() {
        UserDto currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Unauthorized"));
        }
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.getUserNotifications(currentUser.getId()),
                "User notifications"
        ));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount() {
        UserDto currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Unauthorized"));
        }
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.getUnreadCount(currentUser.getId()),
                "Unread notifications count"
        ));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable Long id) {
        UserDto currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Unauthorized"));
        }
        notificationService.markAsRead(id, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(null, "Notification marked as read"));
    }

    @PutMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead() {
        UserDto currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Unauthorized"));
        }
        notificationService.markAllAsRead(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(null, "All notifications marked as read"));
    }
}
