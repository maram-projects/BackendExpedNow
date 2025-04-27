package com.example.ExpedNow.models;

import com.example.ExpedNow.models.enums.PackageType;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.LocalDateTime;
import java.util.Date;

@Data
@Document(collection = "deliveriesRequest")

    public class DeliveryRequest {
    @Id
        private String id;

        private String pickupAddress;

        private String deliveryAddress;

        private String packageDescription;

        private double packageWeight;

        private String vehicleId;



    private PackageType packageType;


    private String additionalInstructions;

        private DeliveryReqStatus status = DeliveryReqStatus.PENDING;


        private String clientId;

        private String deliveryPersonId;



    private LocalDateTime scheduledDate;
    private LocalDateTime createdAt;
    private LocalDateTime assignedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;



        private String notes;

        // For location tracking
        private double pickupLatitude;

        private double pickupLongitude;

        private double deliveryLatitude;

        private double deliveryLongitude;

    public enum DeliveryReqStatus {
        PENDING,         // Initial state
        ASSIGNED,        // Delivery person assigned but not accepted
        APPROVED,        // Delivery person accepted
        IN_TRANSIT,
        DELIVERED,
        CANCELLED
    }


}