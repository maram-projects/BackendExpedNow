package com.example.ExpedNow.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class GoogleMapsService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleMapsService.class);
    private static final String DISTANCE_MATRIX_API_URL = "https://maps.googleapis.com/maps/api/distancematrix/json";
    private static final String GEOCODING_API_URL = "https://maps.googleapis.com/maps/api/geocode/json";
    private static final String API_KEY = "AIzaSyDsywwThSj5QgW0PiooUwr_QCKW15Fm2PE"; // Replace with your real API key

    private final RestTemplate restTemplate;

    public GoogleMapsService() {
        this.restTemplate = new RestTemplate();
    }

    public String calculateDistance(String origin, String destination) {
        try {
            // URL encode the parameters
            String encodedOrigin = URLEncoder.encode(origin, StandardCharsets.UTF_8);
            String encodedDestination = URLEncoder.encode(destination, StandardCharsets.UTF_8);

            String url = String.format("%s?origins=%s&destinations=%s&key=%s",
                    DISTANCE_MATRIX_API_URL, encodedOrigin, encodedDestination, API_KEY);

            logger.info("Calling Distance Matrix API: {}", url);

            // Call the API and return the JSON result as String
            return restTemplate.getForObject(url, String.class);

        } catch (RestClientException e) {
            logger.error("Error calling Distance Matrix API: {}", e.getMessage());

            // Return a fallback JSON structure similar to what Angular expects
            return "{\"rows\":[{\"elements\":[{\"status\":\"ERROR\"}]}]}";
        } catch (Exception e) {
            logger.error("Unexpected error: {}", e.getMessage());
            return "{\"rows\":[{\"elements\":[{\"status\":\"EXCEPTION\"}]}]}";
        }
    }
}
