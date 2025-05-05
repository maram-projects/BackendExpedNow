package com.example.ExpedNow.dto;

import com.example.ExpedNow.models.enums.VehicleType;
import lombok.Data;

import java.util.Date;

@Data
public class VehicleDTO {
    private String id;
    private VehicleType vehicleType;
    private String vehicleBrand;       // Changed from 'make'
    private String vehicleModel;       // Changed from 'model'
    private String vehiclePlateNumber; // Changed from 'licensePlate'
    private String vehicleColor;
    private Integer vehicleYear;      // Changed from 'year'
    private double vehicleCapacityKg;  // Changed from 'maxLoad'
    private double vehicleVolumeM3;
    private boolean vehicleHasFridge;
    private String vehiclePhotoUrl;    // Changed from 'photoPath'
    private Date vehicleInsuranceExpiry;
    private Date vehicleInspectionExpiry;
    private boolean available;
    private Date createdAt;
    private Date updatedAt;

    // Optional: Add calculated field for display
    public String getFullVehicleName() {
        return vehicleYear + " " + vehicleBrand + " " + vehicleModel;
    }


}