package com.example.ExpedNow.controllers;

import com.example.ExpedNow.dto.LocationDTO;
import com.example.ExpedNow.models.Delivery;
import com.example.ExpedNow.models.User;
import com.example.ExpedNow.services.core.impl.AutomatedDeliveryAssignmentServiceImpl;
import com.example.ExpedNow.services.core.impl.DeliveryAssignmentServiceImpl;
import com.example.ExpedNow.services.core.impl.LocationServiceImpl;
import com.example.ExpedNow.services.core.impl.UserServiceImpl;
import com.example.ExpedNow.services.core.impl.DeliveryServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/deliveriesperson") // Make sure this matches your Angular service URL
public class DeliveryAssignmentController {

    @Autowired
    private DeliveryAssignmentServiceImpl deliveryAssignmentService;

    @Autowired
    private AutomatedDeliveryAssignmentServiceImpl automatedDeliveryAssignmentService;

    @Autowired
    private DeliveryServiceImpl deliveryService;

    @Autowired
    private LocationServiceImpl locationService;

    @Autowired
    private UserServiceImpl userService;

    // This endpoint is kept for manual assignment if needed
    @PostMapping("/assign/{deliveryId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Delivery> assignDelivery(@PathVariable String deliveryId) {
        Delivery delivery = deliveryAssignmentService.assignDelivery(deliveryId);
        return ResponseEntity.ok(delivery);
    }

    // This endpoint manually triggers the automated assignment process
    @PostMapping("/trigger-auto-assignment")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> triggerAutomaticAssignment() {
        automatedDeliveryAssignmentService.assignPendingDeliveries();
        return ResponseEntity.ok(Map.of(
                "message", "Automatic assignment process triggered"
        ));
    }

    @GetMapping("/my-deliveries")
    @PreAuthorize("hasAnyRole('ROLE_PROFESSIONAL', 'ROLE_TEMPORARY', 'DELIVERY_PERSON')")
    public ResponseEntity<List<Delivery>> getMyDeliveries(@RequestHeader("Authorization") String token) {
        String userId = userService.getUserIdFromToken(token);
        List<Delivery> deliveries = deliveryAssignmentService.getDeliveriesForPerson(userId);
        return ResponseEntity.ok(deliveries);
    }

    @PutMapping("/{deliveryId}/status")
    @PreAuthorize("hasAnyRole('ROLE_PROFESSIONAL', 'ROLE_TEMPORARY', 'DELIVERY_PERSON')")
    public ResponseEntity<Delivery> updateDeliveryStatus(
            @PathVariable String deliveryId,
            @RequestBody Map<String, String> statusUpdate,
            @RequestHeader("Authorization") String token) {
        String userId = userService.getUserIdFromToken(token);
        Delivery.DeliveryStatus newStatus = Delivery.DeliveryStatus.valueOf(statusUpdate.get("status"));

        Delivery delivery = deliveryAssignmentService.updateDeliveryStatus(deliveryId, newStatus, userId);
        return ResponseEntity.ok(delivery);
    }

    @PostMapping("/location")
    @PreAuthorize("hasAnyRole('ROLE_PROFESSIONAL', 'ROLE_TEMPORARY', 'DELIVERY_PERSON')")
    public ResponseEntity<?> updateLocation(
            @RequestBody LocationDTO location,
            @RequestHeader("Authorization") String token) {
        String userId = userService.getUserIdFromToken(token);
        locationService.updateUserLocation(userId, location.getLatitude(), location.getLongitude());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/availability")
    @PreAuthorize("hasAnyRole('ROLE_PROFESSIONAL', 'ROLE_TEMPORARY', 'DELIVERY_PERSON')")
    public ResponseEntity<User> updateAvailability(
            @RequestBody Map<String, Boolean> availabilityUpdate,
            @RequestHeader("Authorization") String token) {
        String userId = userService.getUserIdFromToken(token);
        boolean available = availabilityUpdate.get("available");

        User user = userService.updateAvailability(userId, available);
        return ResponseEntity.ok(user);
    }
}