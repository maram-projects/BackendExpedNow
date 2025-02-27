package com.example.ExpedNow.dto;

import java.time.LocalDateTime;

public record DeliveryResponseDTO(
        String id,
        String pickupAddress,
        String deliveryAddress,
        String packageDescription,
        double packageWeight,
        String vehicleId,
        LocalDateTime scheduledDate,
        String additionalInstructions,
        String status,
        LocalDateTime createdAt,
        String clientId
) {}