package com.example.ExpedNow.dto;

import com.example.ExpedNow.models.enums.VehicleType;
import lombok.Data;

import java.util.Date;
import java.util.Set;

@Data
public class UserDTO {
    private String id;
    private String firstName;
    private String lastName;
    private String password;
    private String confirmPassword;
    private String userType;
    private String email;
    private String phone;
    private String address;
    private Date dateOfRegistration;

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

    private VehicleDTO assignedVehicle;

    // Account status
    private boolean verified;
    private boolean enabled;
    private boolean available;
    private boolean approved; // Add this missing field

    // Performance metrics
    private double rating;
    private int completedDeliveries;
    private Date lastActive;
    private double successScore;
    private int totalDeliveries;
    private double averageDeliveryTime;

    // Roles
    private Set<String> roles;

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public VehicleDTO getAssignedVehicle() { return assignedVehicle; }
    public void setAssignedVehicle(VehicleDTO vehicle) { this.assignedVehicle = vehicle; }

    // Add getter and setter
    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    // Getter and setter for approved field
    public boolean isApproved() {
        return approved;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }
}