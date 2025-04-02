package com.example.ExpedNow.services;

import com.example.ExpedNow.models.Delivery;
import com.example.ExpedNow.models.Notification;
import com.example.ExpedNow.repositories.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * Sends a notification about a new delivery assignment
     */
    public void sendDeliveryAssignmentNotification(String userId, Delivery delivery) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType("DELIVERY_ASSIGNMENT");
        notification.setTitle("New Delivery Assignment");
        notification.setMessage("You have been assigned a new delivery from " +
                delivery.getPickupAddress() + " to " + delivery.getDeliveryAddress());
        notification.setReferenceId(delivery.getId());
        notification.setCreatedAt(new Date());
        notification.setRead(false);

        Notification savedNotification = notificationRepository.save(notification);

        logger.info("Sending notification to user {}: {}", userId, notification.getMessage());

        messagingTemplate.convertAndSendToUser(userId, "/queue/notifications", savedNotification);
        messagingTemplate.convertAndSend("/topic/notifications", savedNotification);
    }

    /**
     * Gets all unread notifications for a user
     */
    public List<Notification> getUnreadNotifications(String userId) {
        return notificationRepository.findByUserIdAndReadOrderByCreatedAtDesc(userId, false);
    }

    /**
     * Marks a notification as read
     */
    public Notification markAsRead(String notificationId, String userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));

        if (!notification.getUserId().equals(userId)) {
            throw new IllegalArgumentException("This notification doesn't belong to the specified user");
        }

        notification.setRead(true);
        return notificationRepository.save(notification);
    }
}