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

        private Date scheduledDate;

        private String additionalInstructions;

        private DeliveryStatus status = DeliveryStatus.PENDING;

        private Date createdAt = new Date();

        private String clientId;

        private String deliveryPersonId;

        private Date assignedAt;

        private Date startedAt;

        private Date completedAt;

        private String notes;

        // For location tracking
        private double pickupLatitude;

        private double pickupLongitude;

        private double deliveryLatitude;

        private double deliveryLongitude;

        public enum DeliveryStatus {
            PENDING, APPROVED, IN_TRANSIT, DELIVERED, CANCELLED
        }
}