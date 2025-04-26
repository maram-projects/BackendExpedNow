package com.example.ExpedNow.models.enums;


public enum VehicleType {
    MOTORCYCLE(50),      // Can carry up to 50kg
    CAR(200),            // Can carry up to 200kg
    TRUCK(5000),         // Can carry up to 5000kg
    VAN(1000);           // Can carry up to 1000kg

    private final int maxWeight;

    VehicleType(int maxWeight) {
        this.maxWeight = maxWeight;
    }

    public int getMaxWeight() {
        return maxWeight;
    }
}