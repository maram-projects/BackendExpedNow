package com.example.ExpedNow.controllers;

import com.example.ExpedNow.models.Notification;
import com.example.ExpedNow.services.core.impl.NotificationServiceImpl;
import com.example.ExpedNow.services.core.impl.UserServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notifications") // Make sure the prefix is /api/notifications
public class NotificationController {

    @Autowired
    private NotificationServiceImpl notificationService;

    @Autowired
    private UserServiceImpl userService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Notification>> getNotifications(@RequestHeader("Authorization") String token) {
        String userId = userService.getUserIdFromToken(token);
        List<Notification> notifications = notificationService.getUnreadNotifications(userId);
        return ResponseEntity.ok(notifications);
    }

    @PutMapping("/{notificationId}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Notification> markAsRead(
            @PathVariable String notificationId,
            @RequestHeader("Authorization") String token) {
        String userId = userService.getUserIdFromToken(token);
        Notification notification = notificationService.markAsRead(notificationId, userId);
        return ResponseEntity.ok(notification);
    }
}