package com.example.ExpedNow.services.core.impl;

import com.example.ExpedNow.dto.DashboardStatsDTO;
import com.example.ExpedNow.dto.UserDTO;
import com.example.ExpedNow.dto.VehicleDTO;
import com.example.ExpedNow.models.User;
import com.example.ExpedNow.models.Vehicle;
import com.example.ExpedNow.models.enums.Role;
import com.example.ExpedNow.repositories.UserRepository;
import com.example.ExpedNow.repositories.VehicleRepository;
import com.example.ExpedNow.services.core.AdminServiceInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Primary
public class AdminServiceImpl implements AdminServiceInterface {

    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;


    @Autowired
    public AdminServiceImpl(UserRepository userRepository, VehicleRepository vehicleRepository) {
        this.userRepository = userRepository;
        this.vehicleRepository = vehicleRepository;
    }

    @Override
    public DashboardStatsDTO getDashboardStats() {
        DashboardStatsDTO stats = new DashboardStatsDTO();

        // Get all users
        List<User> allUsers = userRepository.findAll();

        // Set total users
        stats.setTotalUsers(allUsers.size());

        // Calculate users by role
        Map<String, Long> usersByRole = allUsers.stream()
                .flatMap(user -> user.getRoles().stream())
                .collect(Collectors.groupingBy(
                        Role::name,
                        Collectors.counting()
                ));
        stats.setUsersByRole(usersByRole);

        // Get recent registrations
        List<UserDTO> recentUsers = userRepository
                .findAll(PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "dateOfRegistration")))
                .getContent()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        stats.setRecentRegistrations(recentUsers);

        return stats;
    }

    @Override
    public List<UserDTO> getAllUsers() {
        return userRepository.findAll(Sort.by(Sort.Direction.DESC, "dateOfRegistration"))
                .stream()
                .map(this::convertToDTOWithVehicle)
                .collect(Collectors.toList());
    }

    private UserDTO convertToDTOWithVehicle(User user) {
        UserDTO dto = convertToDTO(user);

        if (user.getAssignedVehicleId() != null) {
            vehicleRepository.findById(user.getAssignedVehicleId())
                    .ifPresent(vehicle ->
                            dto.setAssignedVehicle(convertToVehicleDTO(vehicle))
                    );
        }

        return dto;
    }

    private VehicleDTO convertToVehicleDTO(Vehicle vehicle) {
        VehicleDTO dto = new VehicleDTO();
        // Direct mappings
        dto.setId(vehicle.getId());
        dto.setVehicleType(vehicle.getVehicleType());
        dto.setAvailable(vehicle.isAvailable());

        // Renamed field mappings
        dto.setVehicleBrand(vehicle.getMake());          // make -> vehicleBrand
        dto.setVehicleModel(vehicle.getModel());         // model -> vehicleModel
        dto.setVehiclePlateNumber(vehicle.getLicensePlate()); // licensePlate -> vehiclePlateNumber
        dto.setVehicleYear(vehicle.getYear());           // year -> vehicleYear
        dto.setVehicleCapacityKg(vehicle.getMaxLoad());  // maxLoad -> vehicleCapacityKg
        dto.setVehiclePhotoUrl(vehicle.getPhotoPath());  // photoPath -> vehiclePhotoUrl

        // Fields without direct equivalents - set defaults or leave null
        dto.setVehicleColor("N/A");                      // Default value
        dto.setVehicleVolumeM3(0.0);                     // Default value
        dto.setVehicleHasFridge(false);                   // Default value

        // Date fields - set to current time if unavailable
        dto.setVehicleInsuranceExpiry(new Date());       // Example default
        dto.setVehicleInspectionExpiry(new Date());      // Example default

        // Timestamps - add if available in entity
        // dto.setCreatedAt(vehicle.getCreatedAt());
        // dto.setUpdatedAt(vehicle.getUpdatedAt());

        return dto;
    }

    @Override
    public void updateUserStatus(String userId, String status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Add status field to User model and implement status update logic
        userRepository.save(user);
    }

    @Override
    public void updateUserRoles(String userId, Set<Role> roles) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Prevent removing ADMIN role from the last admin
        if (user.getRoles().contains(Role.ADMIN) && !roles.contains(Role.ADMIN)) {
            long adminCount = userRepository.findAll().stream()
                    .filter(u -> u.getRoles().contains(Role.ADMIN))
                    .count();
            if (adminCount <= 1) {
                throw new AccessDeniedException("Cannot remove ADMIN role from the last admin user");
            }
        }

        user.setRoles(roles);
        userRepository.save(user);
    }

    private UserDTO convertToDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setAddress(user.getAddress());
        dto.setDateOfRegistration(user.getDateOfRegistration());
        dto.setRoles(user.getRoles().stream().map(Role::name).collect(Collectors.toSet()));
        return dto;
    }
}