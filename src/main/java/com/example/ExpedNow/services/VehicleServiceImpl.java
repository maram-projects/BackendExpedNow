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
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class VehicleServiceImpl implements VehicleService {

    private final VehicleRepository vehicleRepository;
    private final String uploadDir;

    public VehicleServiceImpl(VehicleRepository vehicleRepository,
                              @Value("${file.upload-dir}") String uploadDir) {
        this.vehicleRepository = vehicleRepository;
        this.uploadDir = uploadDir;
        createUploadDirectory();
    }

    private void createUploadDirectory() {
        try {
            Path path = Paths.get(uploadDir);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory", e);
        }
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
        }

        return convertToDTO(vehicleRepository.save(vehicle));
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

    }

    private void updateVehicleFields(Vehicle vehicle, VehicleDTO dto) {
        vehicle.setMake(dto.getMake());
        vehicle.setModel(dto.getModel());
        vehicle.setYear(dto.getYear());
        vehicle.setLicensePlate(dto.getLicensePlate());
        vehicle.setVehicleType(dto.getVehicleType());
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
            Files.copy(photo.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
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

    private VehicleDTO convertToDTO(Vehicle vehicle) {
        VehicleDTO dto = new VehicleDTO();
        BeanUtils.copyProperties(vehicle, dto);
        dto.setAvailable(vehicle.isAvailable());
        dto.setMaxLoad(vehicle.getMaxLoad()); // Add this
        return dto;
    }

    private Vehicle convertToEntity(VehicleDTO dto) {
        Vehicle vehicle = new Vehicle();
        BeanUtils.copyProperties(dto, vehicle);
        vehicle.setAvailable(true); // Force available to true for new vehicles
        vehicle.setMaxLoad(dto.getMaxLoad());
        return vehicle;
    }
    @Override
    public List<VehicleDTO> getAvailableVehicles() {
        return vehicleRepository.findByAvailable(true).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    @Override
    public VehicleDTO setVehicleUnavailable(String id) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + id));

        vehicle.setAvailable(false); // تحديث حالة المركبة إلى غير متاحة
        return convertToDTO(vehicleRepository.save(vehicle));
    }



}