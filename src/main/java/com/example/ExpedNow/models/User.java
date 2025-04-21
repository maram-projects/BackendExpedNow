package com.example.ExpedNow.models;

import com.example.ExpedNow.models.enums.Role;
import com.example.ExpedNow.models.enums.VehicleType;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

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
    private VehicleType vehicleType;

    private double rating;

    private int completedDeliveries;

    private Date lastActive;

    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    public void incrementFailedAttempts() {
        this.failedLoginAttempts++;
    }

    public void resetFailedAttempts() {
        this.failedLoginAttempts = 0;
        this.lockTime = null;
    }

    // Combined availability method
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
        // Example of a custom method to update rating based on business logic
        double currentRating = this.rating;
        int deliveries = this.completedDeliveries;

        if (deliveries == 0) {
            this.rating = newRating;
        } else {
            // Weighted average calculation (just an example)
            this.rating = ((currentRating * deliveries) + newRating) / (deliveries + 1);
        }
        this.completedDeliveries++;
    }

    public void markActive() {
        this.lastActive = new Date();
    }

    private double successScore = 0.0;
    private int totalDeliveries = 0;
    private double averageDeliveryTime = 0.0;
}