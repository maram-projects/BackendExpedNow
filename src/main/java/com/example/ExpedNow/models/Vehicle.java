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
    private boolean available = true;
    private String photoPath;
    private double maxLoad; // Added field
    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public double getMaxLoad() { return maxLoad; }
    public void setMaxLoad(double maxLoad) { this.maxLoad = maxLoad; }
}