package com.restaurant.restaurantpos.controller;

import com.restaurant.restaurantpos.dto.notification.NotificationResponse;
import com.restaurant.restaurantpos.dto.notification.UnreadCountResponse;
import com.restaurant.restaurantpos.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getUserNotifications() {
        List<NotificationResponse> response = notificationService.getUserNotifications();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/unread")
    public ResponseEntity<List<NotificationResponse>> getUnreadUserNotifications() {
        List<NotificationResponse> response = notificationService.getUnreadUserNotifications();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/unread/count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount() {
        UnreadCountResponse response = notificationService.getUnreadCount();
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markAsRead(@PathVariable Long id) {
        NotificationResponse response = notificationService.markAsRead(id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/read-all")
    public ResponseEntity<UnreadCountResponse> markAllAsRead() {
        UnreadCountResponse response = notificationService.markAllAsRead();
        return ResponseEntity.ok(response);
    }
}
