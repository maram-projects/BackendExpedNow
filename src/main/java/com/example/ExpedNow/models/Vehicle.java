package com.example.ExpedNow.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Document(collection = "vehicles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Vehicle {

    @Id
    private String id;

    private String make;
    private String model;
    private Integer year;
    private String licensePlate;
    private VehicleType vehicleType;

    // Photo attribute - storing the file path
    private String photoPath;
}