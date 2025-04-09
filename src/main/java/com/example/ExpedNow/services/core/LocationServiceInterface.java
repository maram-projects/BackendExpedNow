package com.example.ExpedNow.services.core;

import com.example.ExpedNow.dto.LocationDTO;
import com.example.ExpedNow.models.UserLocation;

public interface LocationServiceInterface {
    /**
     * Updates a user's location in the system
     */
    UserLocation updateUserLocation(String userId, double latitude, double longitude);

    /**
     * Gets the last known location for a user
     */
    LocationDTO getLastKnownLocation(String userId);

    /**
     * Gets coordinates for an address (simplified - in a real app, this would call a geocoding API)
     */
    LocationDTO getCoordinates(String address);
}