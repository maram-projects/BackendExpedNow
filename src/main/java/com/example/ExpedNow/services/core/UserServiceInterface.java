package com.example.ExpedNow.services.core;

import com.example.ExpedNow.dto.UserDTO;
import com.example.ExpedNow.models.User;
import com.example.ExpedNow.models.enums.Role;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface UserServiceInterface {
        User findByEmail(String email);

        User findById(String id);

        Collection<GrantedAuthority> getUserAuthorities(String userId);

        User registerUser(User user, Set<Role> roles);

        boolean isEmailTaken(String email);

        void sendVerificationEmail(User user, String token);

        User confirmUser(String token);

        User processOAuth2User(String email, String name);

        User updateProfile(String email, User updatedUser);

        String getUserIdFromToken(String authHeader);

        String getEmailFromToken(String authHeader);

        User updateAvailability(String userId, boolean available);

        ResponseEntity<?> assignVehicle(String userId, String vehicleId);

        List<User> getAllDeliveryPersons();

        List<UserDTO> findAvailableDrivers();

        List<User> findAll();

        User save(User user);

        void deleteById(String id);

        ResponseEntity<?> assignVehicleToUser(String userId, String vehicleId);

        UserDTO getUserByVehicle(String vehicleId);

        List<UserDTO> getAvailableDrivers();

        UserDTO findByAssignedVehicle(String vehicleId);

        ResponseEntity<?> unassignVehicleFromUser(String userId, String vehicleId);
    }
