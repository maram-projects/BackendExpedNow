package com.example.ExpedNow.controllers;

import com.example.ExpedNow.models.Role;
import com.example.ExpedNow.models.User;
import com.example.ExpedNow.security.JwtUtil;
import com.example.ExpedNow.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*") // Add appropriate CORS configuration
public class AuthController {
    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user, @RequestParam String userType) {
        try {
            Set<Role> roles = determineRoles(userType);
            User registeredUser = userService.registerUser(user, roles);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Registration successful");
            response.put("email", registeredUser.getEmail());
            response.put("userType", userType);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody User user) {
        try {
            // Authenticate user
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getEmail(), user.getPassword())
            );

            // Get user details
            User userDetails = userService.findByEmail(user.getEmail());

            // Generate token
            String token = jwtUtil.generateToken(user.getEmail());

            // Create response
            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("userType", determineUserType(userDetails.getRoles()));
            response.put("email", userDetails.getEmail());

            return ResponseEntity.ok(response);
        } catch (AuthenticationException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid email or password"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    private Set<Role> determineRoles(String userType) {
        Set<Role> roles = new HashSet<>();

        switch (userType.toLowerCase()) {
            case "admin":
                roles.add(Role.ROLE_ADMIN);
                break;
            case "client":
                roles.add(Role.ROLE_CLIENT);
                break;
            case "individual":
                roles.add(Role.ROLE_CLIENT);
                roles.add(Role.ROLE_INDIVIDUAL);
                break;
            case "enterprise":
                roles.add(Role.ROLE_CLIENT);
                roles.add(Role.ROLE_ENTERPRISE);
                break;
            case "delivery_person":
                roles.add(Role.ROLE_DELIVERY_PERSON);
                break;
            case "professional":
                roles.add(Role.ROLE_DELIVERY_PERSON);
                roles.add(Role.ROLE_PROFESSIONAL);
                break;
            case "temporary":
                roles.add(Role.ROLE_DELIVERY_PERSON);
                roles.add(Role.ROLE_TEMPORARY);
                break;
            default:
                throw new IllegalArgumentException("Invalid user type: " + userType);
        }

        return roles;
    }

    private String determineUserType(Set<Role> roles) {
        if (roles.contains(Role.ROLE_ADMIN)) {
            return "admin";
        } else if (roles.contains(Role.ROLE_ENTERPRISE)) {
            return "enterprise";
        } else if (roles.contains(Role.ROLE_INDIVIDUAL)) {
            return "individual";
        } else if (roles.contains(Role.ROLE_PROFESSIONAL)) {
            return "professional";
        } else if (roles.contains(Role.ROLE_TEMPORARY)) {
            return "temporary";
        } else if (roles.contains(Role.ROLE_CLIENT)) {
            return "client";
        }
        return "client"; // default fallback
    }
}