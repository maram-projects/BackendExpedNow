package com.example.ExpedNow.services;


import com.example.ExpedNow.dto.VehicleDTO;
import com.example.ExpedNow.exception.ResourceNotFoundException;
import com.example.ExpedNow.models.Vehicle;
import com.example.ExpedNow.repositories.VehicleRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class VehicleServiceImpl implements VehicleService {

    private final VehicleRepository vehicleRepository;

    @Value("${file.upload-dir}")
    private String uploadDir;

    public VehicleServiceImpl(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
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
            String photoPath = savePhoto(photo);
            vehicle.setPhotoPath(photoPath);
        }

        Vehicle savedVehicle = vehicleRepository.save(vehicle);
        return convertToDTO(savedVehicle);
    }

    @Override
    public VehicleDTO updateVehicle(String id, VehicleDTO vehicleDTO, MultipartFile photo) {
        Vehicle existingVehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + id));

        // Update fields
        existingVehicle.setMake(vehicleDTO.getMake());
        existingVehicle.setModel(vehicleDTO.getModel());
        existingVehicle.setYear(vehicleDTO.getYear());
        existingVehicle.setLicensePlate(vehicleDTO.getLicensePlate());
        existingVehicle.setVehicleType(vehicleDTO.getVehicleType());

        // Update photo if provided
        if (photo != null && !photo.isEmpty()) {
            // Delete old photo if exists
            if (existingVehicle.getPhotoPath() != null) {
                deletePhoto(existingVehicle.getPhotoPath());
            }
            String photoPath = savePhoto(photo);
            existingVehicle.setPhotoPath(photoPath);
        }

        Vehicle updatedVehicle = vehicleRepository.save(existingVehicle);
        return convertToDTO(updatedVehicle);
    }

    @Override
    public void deleteVehicle(String id) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + id));

        // Delete photo if exists
        if (vehicle.getPhotoPath() != null) {
            deletePhoto(vehicle.getPhotoPath());
        }

        vehicleRepository.deleteById(id);
    }

    private String savePhoto(MultipartFile photo) {
        try {
            // Create upload directory if it doesn't exist
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Generate unique filename
            String filename = UUID.randomUUID().toString() + "_" + photo.getOriginalFilename();
            Path filePath = uploadPath.resolve(filename);

            // Save file
            Files.copy(photo.getInputStream(), filePath);

            return filename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file", e);
        }
    }

    private void deletePhoto(String photoPath) {
        try {
            Path filePath = Paths.get(uploadDir).resolve(photoPath);
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file", e);
        }
    }

    private VehicleDTO convertToDTO(Vehicle vehicle) {
        VehicleDTO vehicleDTO = new VehicleDTO();
        BeanUtils.copyProperties(vehicle, vehicleDTO);
        return vehicleDTO;
    }

    private Vehicle convertToEntity(VehicleDTO vehicleDTO) {
        Vehicle vehicle = new Vehicle();
        BeanUtils.copyProperties(vehicleDTO, vehicle);
        return vehicle;
    }
}