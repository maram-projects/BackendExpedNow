package com.example.ExpedNow.services.core;

import com.example.ExpedNow.dto.PricingDetailsResponse;
import com.example.ExpedNow.models.DeliveryRequest;

public interface DeliveryPricingService {
    double calculatePrice(DeliveryRequest request);
    PricingDetailsResponse calculateDetailedPrice(DeliveryRequest request); // Add this line
}