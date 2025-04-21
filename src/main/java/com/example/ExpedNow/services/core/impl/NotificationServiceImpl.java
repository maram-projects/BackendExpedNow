package com.example.ExpedNow.services.core.impl;

import com.example.ExpedNow.models.DeliveryRequest;
import com.example.ExpedNow.models.Notification;
import com.example.ExpedNow.repositories.NotificationRepository;
import com.example.ExpedNow.services.core.NotificationServiceInterface;
import com.example.ExpedNow.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
@Primary
public class NotificationServiceImpl implements NotificationServiceInterface {

    private static final Logger logger = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public NotificationServiceImpl(NotificationRepository notificationRepository,
                                   SimpMessagingTemplate messagingTemplate) {
        this.notificationRepository = notificationRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Sends a notification about a new delivery assignment
     */
    @Override
    public void sendDeliveryAssignmentNotification(String userId, DeliveryRequest delivery) {
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
    @Override
    public List<Notification> getUnreadNotifications(String userId) {
        return notificationRepository.findByUserIdAndReadOrderByCreatedAtDesc(userId, false);
    }

    /**
     * Marks a notification as read
     */
    @Override
    public Notification markAsRead(String notificationId, String userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + notificationId));

        if (!notification.getUserId().equals(userId)) {
            throw new IllegalArgumentException("This notification doesn't belong to the specified user");
        }

        notification.setRead(true);
        return notificationRepository.save(notification);
    }
}