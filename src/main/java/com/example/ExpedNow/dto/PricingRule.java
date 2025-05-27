package com.example.ExpedNow.dto;

// في PricingDetailsResponse
public class PricingRule {
    private String description;
    private double amount;

    // getters/setters
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
}