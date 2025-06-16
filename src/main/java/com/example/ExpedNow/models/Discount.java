package com.example.ExpedNow.models;

import com.example.ExpedNow.models.enums.DiscountType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.LocalDateTime;

@Document(collection = "discounts")
@Data
public class Discount {
    @Id
    private String id;

    private String code;
    private String clientId;
    private String description;

    @Field(targetType = FieldType.STRING)
    private DiscountType type;

    // For percentage discounts
    private double percentage;

    // For fixed amount discounts
    private double fixedAmount;

    // Maximum discount amount (optional - for percentage discounts)
    private Double maxAmount;

    // Validity period
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime validFrom;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime validUntil;

    // Usage tracking
    private boolean used = false;
    private LocalDateTime usedAt;
    private String usedForOrderId;

    // Audit fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}