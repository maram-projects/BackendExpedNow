package com.example.ExpedNow.services.core.impl;


import com.example.ExpedNow.dto.VehicleDTO;
import com.example.ExpedNow.exception.ResourceNotFoundException;
import com.example.ExpedNow.models.Vehicle;
import com.example.ExpedNow.repositories.VehicleRepository;
import com.example.ExpedNow.services.core.VehicleServiceInterface;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Primary
public class VehicleServiceImpl implements VehicleServiceInterface {

    private final VehicleRepository vehicleRepository;
    private final String uploadDir;

    public VehicleServiceImpl(VehicleRepository vehicleRepository, @Value("${file.upload-dir}") String uploadDir) {
        this.vehicleRepository = vehicleRepository;
        this.uploadDir = uploadDir;
        createUploadDirectory();
    }



    @Override
    public List<VehicleDTO> getAllVehicles() {
        return vehicleRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public VehicleDTO getVehicleById(String id) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + id));
        return convertToDTO(vehicle);
    }

    @Override
    public VehicleDTO createVehicle(VehicleDTO vehicleDTO, MultipartFile photo) {
        Vehicle vehicle = convertToEntity(vehicleDTO);

        if (photo != null && !photo.isEmpty()) {
            String filename = savePhoto(photo);
            vehicle.setPhotoPath(filename);
            System.out.println("Vehicle photo saved with filename: " + filename);
        }

        Vehicle savedVehicle = vehicleRepository.save(vehicle);
        VehicleDTO result = convertToDTO(savedVehicle);
        System.out.println("Returning vehicle with photo URL: " + result.getVehiclePhotoUrl());
        return result;
    }

    @Override
    public VehicleDTO updateVehicle(String id, VehicleDTO vehicleDTO, MultipartFile photo) {
        Vehicle existingVehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + id));

        updateVehicleFields(existingVehicle, vehicleDTO);

        if (photo != null && !photo.isEmpty()) {
            updateVehiclePhoto(existingVehicle, photo);
        }

        return convertToDTO(vehicleRepository.save(existingVehicle));
    }

    @Override
    public void deleteVehicle(String id) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + id));

        // Delete associated photo if exists
        if (vehicle.getPhotoPath() != null && !vehicle.getPhotoPath().isEmpty()) {
            deletePhoto(vehicle.getPhotoPath());
        }

        // Delete the vehicle from the repository
        vehicleRepository.deleteById(id);
    }

    // In your service class
    private VehicleDTO convertToVehicleDTO(Vehicle vehicle) {
        VehicleDTO dto = new VehicleDTO();
        // Map entity fields to DTO's renamed fields
        dto.setId(vehicle.getId());
        dto.setVehicleBrand(vehicle.getMake());        // make -> vehicleBrand
        dto.setVehicleModel(vehicle.getModel());       // model -> vehicleModel
        dto.setVehicleYear(vehicle.getYear());         // year -> vehicleYear
        dto.setVehiclePlateNumber(vehicle.getLicensePlate());  // licensePlate -> vehiclePlateNumber
        dto.setVehicleType(vehicle.getVehicleType());
        dto.setAvailable(vehicle.isAvailable());
        dto.setVehiclePhotoUrl(vehicle.getPhotoPath()); // photoPath -> vehiclePhotoUrl
        dto.setVehicleCapacityKg(vehicle.getMaxLoad()); // maxLoad -> vehicleCapacityKg

        // Set defaults for non-existing fields
        dto.setVehicleColor("N/A");
        dto.setVehicleVolumeM3(0.0);
        dto.setVehicleHasFridge(false);
        return dto;
    }

    private void updateVehicleFields(Vehicle vehicle, VehicleDTO dto) {
        vehicle.setMake(dto.getVehicleBrand());        // vehicleBrand -> make
        vehicle.setModel(dto.getVehicleModel());       // vehicleModel -> model
        vehicle.setYear(dto.getVehicleYear());         // vehicleYear -> year
        vehicle.setLicensePlate(dto.getVehiclePlateNumber());  // vehiclePlateNumber -> licensePlate
        vehicle.setVehicleType(dto.getVehicleType());
        vehicle.setAvailable(dto.isAvailable());
        vehicle.setPhotoPath(dto.getVehiclePhotoUrl()); // vehiclePhotoUrl -> photoPath
        vehicle.setMaxLoad(dto.getVehicleCapacityKg()); // vehicleCapacityKg -> maxLoad
    }

    private void updateVehiclePhoto(Vehicle vehicle, MultipartFile photo) {
        if (vehicle.getPhotoPath() != null) {
            deletePhoto(vehicle.getPhotoPath());
        }
        vehicle.setPhotoPath(savePhoto(photo));
    }

    private String savePhoto(MultipartFile photo) {
        try {
            String filename = UUID.randomUUID() + "_" + photo.getOriginalFilename();
            Path filePath = Paths.get(uploadDir).resolve(filename);

            // Detailed logging
            System.out.println("Attempting to save file to: " + filePath.toAbsolutePath());
            System.out.println("File size: " + photo.getSize());
            System.out.println("Content type: " + photo.getContentType());

            Files.copy(photo.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            System.out.println("File saved successfully. Accessible at: /uploads/vehicle-photos/" + filename);
            System.out.println("Full URL should be: http://localhost:8080/uploads/vehicle-photos/" + filename);

            return filename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + e.getMessage(), e);
        }
    }




    private void deletePhoto(String filename) {
        try {
            Path filePath = Paths.get(uploadDir).resolve(filename);
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file: " + e.getMessage(), e);
        }
    }

    // بدل ما يكون عندك دالتين منفصلتين
    private VehicleDTO convertToDTO(Vehicle vehicle) {
        VehicleDTO dto = new VehicleDTO();
        // هنا ضيف كل الحقول المطلوبة
        dto.setId(vehicle.getId());
        dto.setVehicleType(vehicle.getVehicleType());
        dto.setVehicleBrand(vehicle.getMake());
        dto.setVehicleModel(vehicle.getModel());
        dto.setVehicleYear(vehicle.getYear());
        dto.setVehiclePlateNumber(vehicle.getLicensePlate());
        dto.setVehicleCapacityKg(vehicle.getMaxLoad());
        dto.setVehiclePhotoUrl(getPhotoUrl(vehicle.getPhotoPath()));
        dto.setAvailable(vehicle.isAvailable());

        // الحقول الإضافية اللي ما عندها مقابل في الموديل
        dto.setVehicleColor("N/A");
        dto.setVehicleVolumeM3(0.0);
        dto.setVehicleHasFridge(false);
        dto.setVehicleInsuranceExpiry(new Date());
        dto.setVehicleInspectionExpiry(new Date());

        return dto;
    }

    private Vehicle convertToEntity(VehicleDTO dto) {
        Vehicle vehicle = new Vehicle();
        // Reverse mapping for renamed fields
        vehicle.setMake(dto.getVehicleBrand());
        vehicle.setModel(dto.getVehicleModel());
        vehicle.setYear(dto.getVehicleYear());
        vehicle.setLicensePlate(dto.getVehiclePlateNumber());
        vehicle.setVehicleType(dto.getVehicleType());
        vehicle.setAvailable(dto.isAvailable());
        vehicle.setPhotoPath(dto.getVehiclePhotoUrl());
        vehicle.setMaxLoad(dto.getVehicleCapacityKg());
        return vehicle;
    }
    @Override
    public List<VehicleDTO> getAvailableVehicles() {
        return vehicleRepository.findByAvailable(true).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    @Override
    public void setVehicleUnavailable(String vehicleId) { // Return type must match interface
        if (vehicleId == null || vehicleId.isEmpty()) {
            return; // Handle missing vehicleId
        }

        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));
        vehicle.setAvailable(false);
        vehicleRepository.save(vehicle);
    }
    private void createUploadDirectory() {
        try {
            Path path = Paths.get(uploadDir);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
            // Add logging to verify
            System.out.println("Upload directory: " + path.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory", e);
        }
    }

    private String getPhotoUrl(String filename) {
        if (filename == null || filename.isEmpty()) {
            return null;
        }
        // Return just the filename, let frontend construct full URL
        return filename;
    }


}