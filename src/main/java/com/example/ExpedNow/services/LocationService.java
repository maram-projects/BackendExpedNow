package com.example.ExpedNow.services;

import com.example.ExpedNow.dto.LocationDTO;
import com.example.ExpedNow.models.UserLocation;
import com.example.ExpedNow.repositories.UserLocationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Optional;

@Service
public class LocationService {

    @Autowired
    private UserLocationRepository userLocationRepository;

    /**
     * Updates a user's location in the system
     */
    public UserLocation updateUserLocation(String userId, double latitude, double longitude) {
        Optional<UserLocation> existingLocation = userLocationRepository.findByUserId(userId);

        UserLocation location;
        if (existingLocation.isPresent()) {
            location = existingLocation.get();
            location.setLatitude(latitude);
            location.setLongitude(longitude);
            location.setLastUpdated(new Date());
        } else {
            location = new UserLocation();
            location.setUserId(userId);
            location.setLatitude(latitude);
            location.setLongitude(longitude);
            location.setLastUpdated(new Date());
        }

        return userLocationRepository.save(location);
    }

    /**
     * Gets the last known location for a user
     */
    public LocationDTO getLastKnownLocation(String userId) {
        Optional<UserLocation> userLocation = userLocationRepository.findByUserId(userId);

        if (userLocation.isPresent()) {
            UserLocation location = userLocation.get();
            return new LocationDTO(location.getLatitude(), location.getLongitude());
        }

        return null;
    }

    /**
     * Gets coordinates for an address (simplified - in a real app, this would call a geocoding API)
     */
    public LocationDTO getCoordinates(String address) {
        // Simplified implementation - in a real app, this would call a geocoding service
        // like Google Maps, Mapbox, etc.
        // For now, returning dummy coordinates
        return new LocationDTO(40.7128, -74.0060); // NYC coordinates as placeholder
    }
}