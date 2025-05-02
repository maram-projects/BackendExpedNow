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

    // Account status
    private boolean verified;
    private boolean enabled;
    private boolean available;

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
}