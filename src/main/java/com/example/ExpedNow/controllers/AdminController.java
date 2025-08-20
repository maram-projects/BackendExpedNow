package com.example.ExpedNow.controllers;

import com.example.ExpedNow.dto.DashboardStatsDTO;
import com.example.ExpedNow.dto.UserDTO;
import com.example.ExpedNow.models.User;
import com.example.ExpedNow.models.enums.Role;
import com.example.ExpedNow.services.core.impl.AdminServiceImpl;
import com.example.ExpedNow.services.core.impl.UserServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@CrossOrigin(origins = "http://localhost:4200")
public class AdminController {

    private final AdminServiceImpl adminService;
    private final UserServiceImpl userService;

    @Autowired
    public AdminController(AdminServiceImpl adminService, UserServiceImpl userService) {
        this.adminService = adminService;
        this.userService = userService;
    }

    // Endpoint pour les statistiques du tableau de bord
    @GetMapping("/dashboard-stats")
    public ResponseEntity<DashboardStatsDTO> getDashboardStats() {
        return ResponseEntity.ok(adminService.getDashboardStats());
    }

    // Endpoint pour les statistiques utilisateurs
    @GetMapping("/stats/users")
    public ResponseEntity<Map<String, Object>> getUsersStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeUsers", userService.countActiveUsers());
        stats.put("totalUsers", userService.countTotalUsers());
        stats.put("inactiveUsers", userService.countInactiveUsers());
        stats.put("usersByRole", userService.countUsersByRole());
        return ResponseEntity.ok(stats);
    }

    // Gestion des utilisateurs avec pagination et filtres
    @GetMapping("/users")
    public ResponseEntity<Page<UserDTO>> getAllUsers(
            @RequestParam(required = false) String searchQuery,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean active,
            Pageable pageable) {
        return ResponseEntity.ok(adminService.getUsersWithFilters(searchQuery, role, active, pageable));
    }

    // Mise à jour du statut utilisateur
    @PutMapping("/users/{userId}/status")
    public ResponseEntity<?> updateUserStatus(
            @PathVariable String userId,
            @RequestBody Map<String, String> statusUpdate) {
        try {
            String status = statusUpdate.get("status");
            User user = userService.findById(userId);

            switch (status.toLowerCase()) {
                case "activate":
                    user.setEnabled(true);
                    user.setApproved(true);
                    break;
                case "deactivate":
                    user.setEnabled(false);
                    break;
                case "approve":
                    user.setApproved(true);
                    break;
                case "reject":
                    user.setApproved(false);
                    break;
                default:
                    return ResponseEntity.badRequest().body(Map.of("error", "Statut invalide"));
            }

            userService.save(user);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Mise à jour des rôles utilisateur
    @PutMapping("/users/{userId}/roles")
    public ResponseEntity<?> updateUserRoles(
            @PathVariable String userId,
            @RequestBody Set<Role> roles) {
        try {
            User user = userService.findById(userId);
            user.setRoles(roles);
            userService.save(user);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Assignation de véhicule à un utilisateur
    @PatchMapping("/users/{userId}/assign-vehicle")
    public ResponseEntity<?> assignVehicleToUser(
            @PathVariable String userId,
            @RequestBody Map<String, String> payload) {
        try {
            if (!payload.containsKey("vehicleId")) {
                return ResponseEntity.badRequest().body("ID du véhicule manquant");
            }
            return userService.assignVehicleToUser(userId, payload.get("vehicleId"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Désassignation de véhicule
    @PatchMapping("/users/{userId}/unassign-vehicle")
    public ResponseEntity<?> unassignVehicleFromUser(
            @PathVariable String userId,
            @RequestBody Map<String, String> request) {
        try {
            if (!request.containsKey("vehicleId")) {
                return ResponseEntity.badRequest().body("ID du véhicule manquant");
            }
            return userService.unassignVehicleFromUser(userId, request.get("vehicleId"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Approbation utilisateur
    @PostMapping("/users/{userId}/approve")
    public ResponseEntity<?> approveUser(@PathVariable String userId) {
        try {
            User user = userService.findById(userId);
            user.setApproved(true);
            userService.save(user);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Rejet utilisateur
    @PostMapping("/users/{userId}/reject")
    public ResponseEntity<?> rejectUser(@PathVariable String userId) {
        try {
            User user = userService.findById(userId);
            user.setApproved(false);
            userService.save(user);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}