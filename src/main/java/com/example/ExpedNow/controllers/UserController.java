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
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userService.findAll());
    }


    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable String id) {
        return ResponseEntity.ok(userService.findById(id));
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
            @RequestBody Map<String, String> request) {
        if (!request.containsKey("vehicleId")) {
            return ResponseEntity.badRequest().body("Missing vehicleId in request");
        }
        String vehicleId = request.get("vehicleId");
        return userService.assignVehicle(userId, vehicleId);
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


}