package com.example.ExpedNow.services.core;

import com.example.ExpedNow.dto.VehicleDTO;
import com.example.ExpedNow.models.Vehicle;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface VehicleServiceInterface {

    List<VehicleDTO> getAllVehicles();

    VehicleDTO getVehicleById(String id);

    VehicleDTO createVehicle(VehicleDTO vehicleDTO, MultipartFile photo);

    VehicleDTO updateVehicle(String id, VehicleDTO vehicleDTO, MultipartFile photo);

    void deleteVehicle(String id);

    List<VehicleDTO> getAvailableVehicles();
    Vehicle findById(String id);


    void setVehicleUnavailable(String vehicleId);
}