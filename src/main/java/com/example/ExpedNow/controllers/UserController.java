package com.example.ExpedNow.controllers;

import com.example.ExpedNow.dto.UserDTO;
import com.example.ExpedNow.models.User;
import com.example.ExpedNow.models.enums.Role;
import com.example.ExpedNow.services.core.impl.UserServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "http://localhost:4200")
public class UserController {

    private final UserServiceImpl userService;

    @Autowired
    public UserController(UserServiceImpl userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        List<User> users = userService.findAll();
        List<UserDTO> userDTOs = users.stream()
                .map(user -> {
                    UserDTO dto = convertToDTO(user);
                    dto.setUserType(determineUserType(user.getRoles()));
                    dto.setApproved(user.isApproved()); // تأكد من وجود هذه السطور
                    dto.setEnabled(user.isEnabled());
                    return dto;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(userDTOs);
    }
    private String determineUserType(Set<Role> roles) {
        if (roles.contains(Role.ADMIN)) {
            return "admin";
        }
        if (roles.contains(Role.ROLE_ENTERPRISE)) {
            return "enterprise";
        }
        if (roles.contains(Role.ROLE_INDIVIDUAL)) {
            return "individual";
        }
        if (roles.contains(Role.ROLE_PROFESSIONAL)) {
            return "professional";
        }
        if (roles.contains(Role.ROLE_TEMPORARY)) {
            return "temporary";
        }
        return "unknown";
    }
    @PreAuthorize("hasAuthority('ADMIN')")
    @PostMapping("/reject-user/{userId}")
    public ResponseEntity<?> rejectUser(@PathVariable String userId) {
        try {
            userService.rejectUser(userId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @PatchMapping("/{userId}/enable")
    public ResponseEntity<User> enableUser(@PathVariable String userId) {
        User user = userService.findById(userId);
        user.setEnabled(true);
        user.setApproved(true); // Enable typically means approve as well
        User updatedUser = userService.save(user);
        return ResponseEntity.ok(updatedUser);
    }



    @PreAuthorize("hasAuthority('ADMIN')")
    @PatchMapping("/{userId}/disable")
    public ResponseEntity<User> disableUser(@PathVariable String userId) {
        User user = userService.findById(userId);
        user.setEnabled(false);
        User updatedUser = userService.save(user);
        return ResponseEntity.ok(updatedUser);
    }
    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable String id) {
        return ResponseEntity.ok(userService.findById(id));
    }
    @PutMapping("/{id}")
    public ResponseEntity<User> updateUser(@PathVariable String id, @RequestBody User updatedUser) {
        User existingUser = userService.findById(id);

        // Update only allowed fields
        existingUser.setFirstName(updatedUser.getFirstName());
        existingUser.setLastName(updatedUser.getLastName());
        existingUser.setPhone(updatedUser.getPhone());
        existingUser.setAddress(updatedUser.getAddress());

        // For admin updates
        if (updatedUser.getRoles() != null) {
            existingUser.setRoles(updatedUser.getRoles());
        }
        if (updatedUser.isEnabled() != existingUser.isEnabled()) {
            existingUser.setEnabled(updatedUser.isEnabled());
        }
        if (updatedUser.isApproved() != existingUser.isApproved()) {
            existingUser.setApproved(updatedUser.isApproved());
        }

        User savedUser = userService.save(existingUser);
        return ResponseEntity.ok(savedUser);
    }
    @GetMapping("/me")
    public ResponseEntity<User> getCurrentUser(Principal principal) {
        User user = userService.findByEmail(principal.getName());
        return ResponseEntity.ok(user);
    }

    @PutMapping("/profile")
    public ResponseEntity<User> updateProfile(@RequestBody User updatedUser, Principal principal) {
        String email = principal.getName();
        User user = userService.updateProfile(email, updatedUser);
        return ResponseEntity.ok(user);
    }

    @GetMapping("/delivery")
    public ResponseEntity<List<User>> getAllDeliveryPersons() {
        return ResponseEntity.ok(userService.getAllDeliveryPersons());
    }

    @GetMapping("/available-drivers")
    public ResponseEntity<List<UserDTO>> getAvailableDrivers() {
        return ResponseEntity.ok(userService.getAvailableDrivers());
    }

    @GetMapping("/by-vehicle/{vehicleId}")
    public ResponseEntity<UserDTO> getUserByAssignedVehicle(@PathVariable String vehicleId) {
        UserDTO user = userService.findByAssignedVehicle(vehicleId);
        return ResponseEntity.ok(user);
    }

    @PatchMapping("/{userId}/assign-vehicle")
    public ResponseEntity<?> assignVehicleToUser(
            @PathVariable String userId,
            @RequestBody Map<String, String> payload) {

        if (!payload.containsKey("vehicleId")) {
            return ResponseEntity.badRequest().body("Missing vehicleId in request");
        }

        String vehicleId = payload.get("vehicleId");
        return userService.assignVehicleToUser(userId, vehicleId);
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @PostMapping("/approve-user/{userId}")
    public ResponseEntity<?> approveUser(@PathVariable String userId) {
        try {
            User user = userService.approveUser(userId);
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{userId}/unassign-vehicle")
    public ResponseEntity<?> unassignVehicleFromUser(
            @PathVariable String userId,
            @RequestBody Map<String, String> request) {
        if (!request.containsKey("vehicleId")) {
            return ResponseEntity.badRequest().body("Missing vehicleId in request");
        }
        String vehicleId = request.get("vehicleId");
        return userService.unassignVehicleFromUser(userId, vehicleId);
    }

    @GetMapping("/delivery-personnel")
    public ResponseEntity<List<UserDTO>> getAllDeliveryPersonnel() {
        return ResponseEntity.ok(userService.getAllDeliveryPersons()
                .stream()
                .map(userService::convertToDTO)
                .collect(Collectors.toList()));
    }

    // Add this method to your UserController class
    private UserDTO convertToDTO(User user) {
        UserDTO dto = new UserDTO();

        // Basic fields
        dto.setId(user.getId());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setAddress(user.getAddress());
        dto.setDateOfRegistration(user.getDateOfRegistration());

        // Enterprise fields
        dto.setCompanyName(user.getCompanyName());
        dto.setBusinessType(user.getBusinessType());
        dto.setVatNumber(user.getVatNumber());
        dto.setBusinessPhone(user.getBusinessPhone());
        dto.setBusinessAddress(user.getBusinessAddress());
        dto.setDeliveryRadius(user.getDeliveryRadius());

        // Vehicle fields
        dto.setVehicleType(user.getVehicleType());
        dto.setVehicleBrand(user.getVehicleBrand());
        dto.setVehicleModel(user.getVehicleModel());
        dto.setVehiclePlateNumber(user.getVehiclePlateNumber());
        dto.setVehicleColor(user.getVehicleColor());
        dto.setVehicleYear(user.getVehicleYear());
        dto.setVehicleCapacityKg(user.getVehicleCapacityKg());
        dto.setVehicleVolumeM3(user.getVehicleVolumeM3());
        dto.setVehicleHasFridge(user.isVehicleHasFridge());
        dto.setVehiclePhotoUrl(user.getVehiclePhotoUrl());
        dto.setVehicleInsuranceExpiry(user.getVehicleInsuranceExpiry());
        dto.setVehicleInspectionExpiry(user.getVehicleInspectionExpiry());
        dto.setAssignedVehicleId(user.getAssignedVehicleId());

        // Professional fields
        dto.setDriverLicenseNumber(user.getDriverLicenseNumber());
        dto.setDriverLicenseCategory(user.getDriverLicenseCategory());
        dto.setDriverLicenseIssueDate(user.getDriverLicenseIssueDate());
        dto.setDriverLicenseExpiryDate(user.getDriverLicenseExpiryDate());
        dto.setIdentityPhotoUrl(user.getIdentityPhotoUrl());
        dto.setCriminalRecordDocumentUrl(user.getCriminalRecordDocumentUrl());
        dto.setMedicalCertificateUrl(user.getMedicalCertificateUrl());
        dto.setPreferredZones(user.getPreferredZones());
        dto.setAvailabilitySchedule(user.getAvailabilitySchedule());
        dto.setHasCompanyAffiliation(user.isHasCompanyAffiliation());

        // Account status
        dto.setVerified(user.isVerified());
        dto.setEnabled(user.isEnabled());
        dto.setAvailable(user.isAvailable());

        // Performance metrics
        dto.setRating(user.getRating());
        dto.setCompletedDeliveries(user.getCompletedDeliveries());
        dto.setLastActive(user.getLastActive());
        dto.setSuccessScore(user.getSuccessScore());
        dto.setTotalDeliveries(user.getTotalDeliveries());
        dto.setAverageDeliveryTime(user.getAverageDeliveryTime());
        dto.setRating(user.getRating());
        dto.setRatingCount(user.getRatingCount());
        // Roles - convert Set<Role> to Set<String>
        if (user.getRoles() != null) {
            dto.setRoles(user.getRoles().stream()
                    .map(Role::name)
                    .collect(Collectors.toSet()));
        }

        // Set assigned vehicle if exists
        if (user.getAssignedVehicle() != null) {
            // You'll need to convert Vehicle to VehicleDTO here
            // dto.setAssignedVehicle(convertVehicleToDTO(user.getAssignedVehicle()));
        }

        return dto;
    }
}