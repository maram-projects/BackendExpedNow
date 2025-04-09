package com.example.ExpedNow.services.core;

import com.example.ExpedNow.models.Delivery;
import com.example.ExpedNow.models.Notification;

import java.util.List;

public interface NotificationServiceInterface {

    void sendDeliveryAssignmentNotification(String userId, Delivery delivery);

    List<Notification> getUnreadNotifications(String userId);

    Notification markAsRead(String notificationId, String userId);
}