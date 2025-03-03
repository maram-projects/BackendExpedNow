package com.example.ExpedNow.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

public record DeliveryResponseDTO(
        String id,
        String pickupAddress,
        String deliveryAddress,
        String packageDescription,
        double packageWeight,
        String vehicleId,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
        Date scheduledDate,
        String additionalInstructions,
        String status,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
        Date createdAt,
        String clientId
) {}
