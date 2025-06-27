package com.example.ExpedNow.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DeliveryPaymentDto {
    private String paymentId;
    private String deliveryPersonId;
    private double amount;
    private double deliveryShare;
    private LocalDateTime paymentDate;
}