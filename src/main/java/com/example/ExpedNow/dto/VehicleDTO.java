package com.example.ExpedNow.dto;

import com.example.ExpedNow.models.VehicleType;
import lombok.Data;

@Data
public class VehicleDTO {
    private String id;
    private String make;
    private String model;
    private Integer year;
    private String licensePlate;
    private VehicleType vehicleType;
    private String photoPath;
    private boolean available;
    private double maxLoad;
}