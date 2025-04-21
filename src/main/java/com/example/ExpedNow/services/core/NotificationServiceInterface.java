package com.example.ExpedNow.services.core;

import com.example.ExpedNow.models.DeliveryRequest;
import com.example.ExpedNow.models.Notification;

import java.util.List;

public interface NotificationServiceInterface {

    void sendDeliveryAssignmentNotification(String userId, DeliveryRequest delivery);

    List<Notification> getUnreadNotifications(String userId);

    Notification markAsRead(String notificationId, String userId);
}