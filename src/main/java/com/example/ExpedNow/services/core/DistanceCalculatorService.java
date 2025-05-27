package com.example.ExpedNow.services.core;

public interface DistanceCalculatorService {
    double calculateDistanceKm(double startLat, double startLon, double endLat, double endLon);
}