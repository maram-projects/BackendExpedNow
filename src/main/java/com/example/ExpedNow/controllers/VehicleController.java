package com.example.ExpedNow.controllers;


import com.example.ExpedNow.dto.VehicleDTO;
import com.example.ExpedNow.models.Vehicle;
import com.example.ExpedNow.services.VehicleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
@CrossOrigin(origins = "http://localhost:4200")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @GetMapping
    public ResponseEntity<List<VehicleDTO>> getAllVehicles() {
        return ResponseEntity.ok(vehicleService.getAllVehicles());
    }

    @GetMapping("/available")
    public ResponseEntity<List<VehicleDTO>> getAvailableVehicles() { // Changed return type to VehicleDTO
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
}