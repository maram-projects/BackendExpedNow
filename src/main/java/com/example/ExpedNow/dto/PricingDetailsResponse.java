package com.example.ExpedNow.dto;

import lombok.Data;
import java.util.List;

@Data
public class PricingDetailsResponse {
    private double totalAmount;
    private double basePrice;
    private double distance;
    private double distanceCost;
    private double weightCost;
    private double urgencyFee;
    private double peakSurcharge;
    private double holidaySurcharge;
    private double discountAmount;
    private List<PricingRule> appliedRules;

    @Data
    public static class PricingRule {
        private String description;
        private double amount;
    }
}