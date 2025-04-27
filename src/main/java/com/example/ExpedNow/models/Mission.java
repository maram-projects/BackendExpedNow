package com.example.ExpedNow.models;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import java.time.LocalDateTime;

/**
 * Represents a delivery mission assigned to a delivery person
 */
@Document(collection = "missions")
@Data
public class Mission {
    @Id
    private String id;

    @DBRef
    private DeliveryRequest deliveryRequest;

    @DBRef
    private User deliveryPerson;

    @Field(targetType = FieldType.STRING)
    private String status; // PENDING, IN_PROGRESS, COMPLETED, CANCELLED

    @Field(targetType = FieldType.DATE_TIME)
    private LocalDateTime startTime;

    @Field(targetType = FieldType.DATE_TIME)
    private LocalDateTime endTime;

    private String notes;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}