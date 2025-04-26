package com.example.ExpedNow.models;

import com.example.ExpedNow.models.enums.Role;
import com.example.ExpedNow.models.enums.VehicleType;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "users")
public class User {
    @Id
    private String id;

    private String firstName;
    private String lastName;
    private String email;
    private String password;
    private String phone;
    private String address;
    private Date dateOfRegistration;

    @Field("vehicle_type")  // MongoDB field name customization
    private VehicleType vehicleType;

    private String assignedVehicleId;

    @Builder.Default
    private boolean verified = false;

    @Builder.Default
    private boolean enabled = true;

    @Builder.Default
    private boolean available = true;

    @Builder.Default
    private int failedLoginAttempts = 0;

    private LocalDateTime lockTime;

    // For delivery persons
    private double rating;
    private int completedDeliveries;
    private Date lastActive;

    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    // Rest of the methods remain unchanged...
    public void incrementFailedAttempts() {
        this.failedLoginAttempts++;
    }

    public void resetFailedAttempts() {
        this.failedLoginAttempts = 0;
        this.lockTime = null;
    }

    public boolean isAvailable() {
        return this.enabled && this.available;
    }

    public void addRole(Role role) {
        this.roles.add(role);
    }

    public void removeRole(Role role) {
        this.roles.remove(role);
    }

    public boolean hasRole(Role role) {
        return this.roles.contains(role);
    }

    public void updateRating(double newRating) {
        if (this.completedDeliveries == 0) {
            this.rating = newRating;
        } else {
            this.rating = ((this.rating * this.completedDeliveries) + newRating) / (this.completedDeliveries + 1);
        }
        this.completedDeliveries++;
    }

    public void markActive() {
        this.lastActive = new Date();
    }

    private double successScore = 0.0;
    private int totalDeliveries = 0;
    private double averageDeliveryTime = 0.0;

    // In your User class
    public String getId() {
        return this.id;
    }

    public String getEmail() {  // Should match what you're using for username
        return this.email;
    }

    public String getFullName() {
        return (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
    }

}