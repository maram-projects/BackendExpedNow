package com.example.ExpedNow.models;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.Date;

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
    private Date scheduledDate;      // Changed to java.util.Date
    private String additionalInstructions;
    private DeliveryStatus status = DeliveryStatus.PENDING;
    private Date createdAt = new Date(); // Changed to java.util.Date
    private String clientId;

    public enum DeliveryStatus {
        PENDING, APPROVED, IN_TRANSIT, DELIVERED, CANCELLED
    }
}