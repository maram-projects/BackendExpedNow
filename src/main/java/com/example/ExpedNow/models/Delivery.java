package com.example.ExpedNow.models;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@Document(collection = "deliveries")
public class Delivery {
    @Id
    private String id;
    private String pickupAddress;
    private String deliveryAddress;
    private String packageDescription;
    private double packageWeight;
    private String vehicleId;
    private LocalDateTime scheduledDate;
    private String additionalInstructions;
    private DeliveryStatus status = DeliveryStatus.PENDING;
    private LocalDateTime createdAt = LocalDateTime.now();
    private String clientId;

    public enum DeliveryStatus {
        PENDING, APPROVED, IN_TRANSIT, DELIVERED, CANCELLED
    }
}