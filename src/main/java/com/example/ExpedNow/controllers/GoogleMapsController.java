package com.example.ExpedNow.controllers;

import com.example.ExpedNow.services.GoogleMapsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/maps")
public class GoogleMapsController {

    private final GoogleMapsService googleMapsService;

    public GoogleMapsController(GoogleMapsService googleMapsService) {
        this.googleMapsService = googleMapsService;
    }

    @GetMapping("/distance")
    public ResponseEntity<String> calculateDistance(
            @RequestParam String origin,
            @RequestParam String destination) {
        String result = googleMapsService.calculateDistance(origin, destination);
        return ResponseEntity.ok(result);
    }
}