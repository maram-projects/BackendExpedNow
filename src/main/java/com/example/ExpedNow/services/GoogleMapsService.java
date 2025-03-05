package com.example.ExpedNow.services;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class GoogleMapsService {

    private static final String DISTANCE_MATRIX_API_URL = "https://maps.googleapis.com/maps/api/distancematrix/json";
    private static final String API_KEY = "AIzaSyD-7IDvD5TY4ruaYETkCO_iWaIibZWE5WE";

    public String calculateDistance(String origin, String destination) {
        String url = String.format("%s?origins=%s&destinations=%s&key=%s",
                DISTANCE_MATRIX_API_URL, origin, destination, API_KEY);

        RestTemplate restTemplate = new RestTemplate();
        String response = restTemplate.getForObject(url, String.class);
        return response; // Parse the response to extract distance
    }
}