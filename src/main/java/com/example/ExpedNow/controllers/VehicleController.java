package com.example.ExpedNow.controllers;

import com.example.ExpedNow.dto.VehicleDTO;
import com.example.ExpedNow.models.Vehicle;
import com.example.ExpedNow.services.core.VehicleServiceInterface;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vehicles")
@CrossOrigin(origins = "http://localhost:4200")
public class VehicleController {

    private final VehicleServiceInterface vehicleService;

    public VehicleController(VehicleServiceInterface vehicleService) {
        this.vehicleService = vehicleService;
    }

    @PatchMapping("/{id}/set-unavailable")
    public ResponseEntity<Void> setVehicleUnavailable(@PathVariable String id) {
        vehicleService.setVehicleUnavailable(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<VehicleDTO>> getAllVehicles() {
        return ResponseEntity.ok(vehicleService.getAllVehicles());
    }

    @GetMapping("/available")
    public ResponseEntity<List<VehicleDTO>> getAvailableVehicles() {
        List<VehicleDTO> availableVehicles = vehicleService.getAvailableVehicles();
        return ResponseEntity.ok(availableVehicles);
    }

    @GetMapping("/{id}")
    public ResponseEntity<VehicleDTO> getVehicleById(@PathVariable String id) {
        return ResponseEntity.ok(vehicleService.getVehicleById(id));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VehicleDTO> createVehicle(
            @RequestPart("vehicle") VehicleDTO vehicleDTO,
            @RequestPart(value = "photo", required = false) MultipartFile photo) {
        return new ResponseEntity<>(vehicleService.createVehicle(vehicleDTO, photo), HttpStatus.CREATED);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VehicleDTO> updateVehicle(
            @PathVariable String id,
            @RequestPart("vehicle") VehicleDTO vehicleDTO,
            @RequestPart(value = "photo", required = false) MultipartFile photo) {
        return ResponseEntity.ok(vehicleService.updateVehicle(id, vehicleDTO, photo));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVehicle(@PathVariable String id) {
        vehicleService.deleteVehicle(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/assign")
    public ResponseEntity<VehicleDTO> assignVehicleToUser(
            @PathVariable String id,
            @RequestBody Map<String, String> payload) {
        String userId = payload.get("userId");

        // First, get the vehicle
        VehicleDTO vehicle = vehicleService.getVehicleById(id);

        // Set the vehicle as unavailable
        vehicleService.setVehicleUnavailable(id);

        // Here you would typically call a service method to associate the vehicle with a user
        // For example: vehicleService.assignVehicleToUser(id, userId);

        // For now, return the updated vehicle
        VehicleDTO updatedVehicle = vehicleService.getVehicleById(id);
        return ResponseEntity.ok(updatedVehicle);
    }
}