package com.example.ExpedNow.models;

import com.example.ExpedNow.models.enums.Role;
import com.example.ExpedNow.models.enums.VehicleType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
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
@JsonIgnoreProperties(ignoreUnknown = true)

public class User {
    @Id
    private String id;
    private String firstName;
    private String lastName;
    private String email;
    private String password;
    private String resetToken;               // لحفظ رمز إعادة التعيين
    private LocalDateTime resetTokenExpiry;
    @Transient
    private String confirmPassword;
    private LocalDateTime tokenExpiryDate;
    private String phone;
    private String address;
    private Date dateOfRegistration;
    private double balance = 0.0;
    @Builder.Default
    private boolean approved = false;
    @Transient // This annotation means the field won't be persisted in the database
    private String userType;

    @Builder.Default
    private boolean enabled = false;

    // Enterprise fields
    private String companyName;
    private String businessType;
    private String vatNumber;
    private String businessPhone;
    private String businessAddress;
    private double deliveryRadius;

    // Vehicle fields
    private VehicleType vehicleType;
    private String vehicleBrand;
    private String vehicleModel;
    private String vehiclePlateNumber;
    private String vehicleColor;
    private int vehicleYear;
    private double vehicleCapacityKg;
    private double vehicleVolumeM3;
    private boolean vehicleHasFridge;
    private String vehiclePhotoUrl;
    private Date vehicleInsuranceExpiry;
    private Date vehicleInspectionExpiry;
    private String assignedVehicleId;

    // Professional fields
    private String driverLicenseNumber;
    private String driverLicenseCategory;
    private Date driverLicenseIssueDate;
    private Date driverLicenseExpiryDate;
    private String identityPhotoUrl;
    private String criminalRecordDocumentUrl;
    private String medicalCertificateUrl;
    private String preferredZones;
    private String availabilitySchedule;
    private boolean hasCompanyAffiliation;

    // Account status
    @Builder.Default
    private boolean verified = true;

    @Builder.Default
    private boolean available = true;

    @Builder.Default
    private int failedLoginAttempts = 0;  // Added initialization

    private LocalDateTime lockTime;

    @Transient
    private Vehicle assignedVehicle;

    // Business methods
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

    public String getFullName() {
        return (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
    }

    // Roles
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    // Performance metrics
    private double rating;
    private int completedDeliveries;
    private Date lastActive;
    private double successScore;
    private int totalDeliveries;
    private double averageDeliveryTime;


    public String getResetToken() {
        return resetToken;
    }

    public void setResetToken(String resetToken) {
        this.resetToken = resetToken;
    }

    public LocalDateTime getTokenExpiryDate() {
        return tokenExpiryDate;
    }

    public void setTokenExpiryDate(LocalDateTime tokenExpiryDate) {
        this.tokenExpiryDate = tokenExpiryDate;
    }

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }
}