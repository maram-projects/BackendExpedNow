package com.example.ExpedNow.models;

public enum VehicleType {
    CAR,
    TRUCK,
    MOTORCYCLE;

    // Add for proper case conversion
    public String getDisplayName() {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }
}