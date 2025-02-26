package com.example.ExpedNow.services;

import com.example.ExpedNow.dto.DashboardStatsDTO;
import com.example.ExpedNow.dto.UserDTO;
import com.example.ExpedNow.models.Role;
import com.example.ExpedNow.models.User;
import com.example.ExpedNow.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminService {

    @Autowired
    private UserRepository userRepository;

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

    public List<UserDTO> getAllUsers() {
        return userRepository.findAll(Sort.by(Sort.Direction.DESC, "dateOfRegistration"))
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public void updateUserStatus(String userId, String status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Add status field to User model and implement status update logic
        userRepository.save(user);
    }

    public void updateUserRoles(String userId, Set<Role> roles) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Prevent removing ADMIN role from the last admin
        if (user.getRoles().contains(Role.ROLE_ADMIN) && !roles.contains(Role.ROLE_ADMIN)) {
            long adminCount = userRepository.findAll().stream()
                    .filter(u -> u.getRoles().contains(Role.ROLE_ADMIN))
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