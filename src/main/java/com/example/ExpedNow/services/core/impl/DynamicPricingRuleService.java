package com.example.ExpedNow.services.core.impl;

import com.example.ExpedNow.models.DeliveryRequest;
import com.example.ExpedNow.services.core.DistanceCalculatorService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DynamicPricingRuleService {

    @Value("${pricing.rules.rule1}") // Directly inject the value
    private String rule1Expression;

    private final DistanceCalculatorService distanceCalculator;

    public DynamicPricingRuleService(DistanceCalculatorService distanceCalculator) {
        this.distanceCalculator = distanceCalculator;
    }

    public double applyDynamicRules(DeliveryRequest request, double initialPrice) {
        // Example rule: If distance > 10km and weight > 5kg, add 4 DZD
        double distance = distanceCalculator.calculateDistanceKm(
                request.getPickupLatitude(),
                request.getPickupLongitude(),
                request.getDeliveryLatitude(),
                request.getDeliveryLongitude()
        );

        if (request.getPackageWeight() > 5 && distance > 10) {
            return initialPrice + 4.0;
        }
        return initialPrice;
    }
}