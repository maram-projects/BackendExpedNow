package com.example.ExpedNow.dto;

import com.example.ExpedNow.models.User;
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
        String clientId,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
        Date updatedAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
        Date assignedAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
        Date startedAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
        Date completedAt,
        Double pickupLatitude,
        Double pickupLongitude,
        Double deliveryLatitude,
        Double deliveryLongitude,
        Double rating,
        boolean rated,
        UserDTO deliveryPerson,  // أضف هذا
        VehicleDTO assignedVehicle
) {}

