package com.example.ExpedNow.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public record DeliveryRequestDTO(
        @NotBlank String pickupAddress,
        @NotBlank String deliveryAddress,
        @NotBlank String packageDescription,
        @Positive double packageWeight,
        @NotBlank String vehicleId,
        @Future LocalDateTime scheduledDate,
        String additionalInstructions
) {}