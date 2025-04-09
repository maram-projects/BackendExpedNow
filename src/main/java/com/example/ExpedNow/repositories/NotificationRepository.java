package com.example.ExpedNow.repositories;

import com.example.ExpedNow.models.Notification;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface NotificationRepository extends MongoRepository<Notification, String> {

    List<Notification> findByUserIdAndReadOrderByCreatedAtDesc(String userId, boolean read);
}