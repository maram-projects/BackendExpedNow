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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
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
                        role -> role.name().replace("ROLE_", ""), // Remove ROLE_ prefix for cleaner display
                        Collectors.counting()
                ));
        stats.setUsersByRole(usersByRole);

        // Count delivery persons
        long deliveryPersonCount = allUsers.stream()
                .filter(user -> user.getRoles().contains(Role.ROLE_DELIVERY_PERSON))
                .count();
        stats.setDeliveryPersons((int) deliveryPersonCount);

        // Count active users today (users who registered today - you can modify this logic)
        LocalDate today = LocalDate.now();
        long activeTodayCount = allUsers.stream()
                .filter(user -> {
                    if (user.getDateOfRegistration() != null) {
                        LocalDate regDate = user.getDateOfRegistration().toInstant()
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate();
                        return regDate.equals(today);
                    }
                    return false;
                })
                .count();
        stats.setActiveToday((int) activeTodayCount);

        // Set pending approvals (you can implement this based on your business logic)
        // For now, let's assume users without certain roles are pending
        long pendingApprovals = allUsers.stream()
                .filter(user -> user.getRoles().isEmpty())
                .count();
        stats.setPendingApprovals((int) pendingApprovals);

        // Get recent registrations
        List<UserDTO> recentUsers = userRepository
                .findAll(PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "dateOfRegistration")))
                .getContent()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        stats.setRecentRegistrations(recentUsers);

        // Mock payment data (replace with actual payment repository calls)
        stats.setTotalPayments(150); // Mock value
        stats.setTotalRevenue(25000.0); // Mock value

        // Mock payment status breakdown
        Map<String, Integer> paymentStatusBreakdown = new HashMap<>();
        paymentStatusBreakdown.put("COMPLETED", 120);
        paymentStatusBreakdown.put("PENDING", 20);
        paymentStatusBreakdown.put("FAILED", 10);
        stats.setPaymentStatusBreakdown(paymentStatusBreakdown);

        // Mock discount data
        stats.setTotalDiscounts(25);
        stats.setActiveDiscounts(15);

        Map<String, Integer> discountTypeBreakdown = new HashMap<>();
        discountTypeBreakdown.put("PERCENTAGE", 15);
        discountTypeBreakdown.put("FIXED_AMOUNT", 8);
        discountTypeBreakdown.put("FREE_DELIVERY", 2);
        stats.setDiscountTypeBreakdown(discountTypeBreakdown);

        // Mock bonus data
        stats.setTotalBonuses(45);
        stats.setBonusAmountPaid(3500.0);

        Map<String, Integer> bonusStatusBreakdown = new HashMap<>();
        bonusStatusBreakdown.put("PAID", 30);
        bonusStatusBreakdown.put("PENDING", 12);
        bonusStatusBreakdown.put("CANCELLED", 3);
        stats.setBonusStatusBreakdown(bonusStatusBreakdown);

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