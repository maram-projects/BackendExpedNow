package com.example.ExpedNow.controllers;

import com.example.ExpedNow.models.Role;
import com.example.ExpedNow.models.User;
import com.example.ExpedNow.repositories.UserRepository;
import com.example.ExpedNow.security.CustomUserDetailsService;
import com.example.ExpedNow.security.JwtUtil;
import com.example.ExpedNow.services.UserService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:4200")
public class AuthController {
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_TIME_MINUTES = 30;

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    // Inject authentication manager directly instead of through SecurityConfig
    public AuthController(AuthenticationManager authenticationManager,
                          UserService userService,
                          UserRepository userRepository,
                          JwtUtil jwtUtil) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user,
                                      @RequestParam String userType) {
        try {
            if (user.getEmail() == null || user.getPassword() == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Email and password are required"));
            }

            if (userService.isEmailTaken(user.getEmail())) {
                return ResponseEntity.badRequest().body(Map.of("message", "Email already exists"));
            }

            if (user.getDateOfRegistration() == null) {
                user.setDateOfRegistration(new Date());
            }

            EnumSet<Role> roles = EnumSet.noneOf(Role.class);

            switch (userType.toLowerCase()) {
                case "individual":
                    roles.add(Role.ROLE_CLIENT);
                    roles.add(Role.ROLE_INDIVIDUAL);
                    break;
                case "enterprise":
                    roles.add(Role.ROLE_CLIENT);
                    roles.add(Role.ROLE_ENTERPRISE);
                    break;
                case "professional":
                    roles.add(Role.ROLE_DELIVERY_PERSON);
                    roles.add(Role.ROLE_PROFESSIONAL);
                    break;
                case "temporary":
                    roles.add(Role.ROLE_DELIVERY_PERSON);
                    roles.add(Role.ROLE_TEMPORARY);
                    break;
                case "admin":
                    roles.add(Role.ADMIN);
                    break;
                default:
                    roles.add(Role.ROLE_CLIENT);
                    roles.add(Role.ROLE_INDIVIDUAL);
            }

            try {
                User registeredUser = userService.registerUser(user, roles);
                String token = jwtUtil.generateToken(registeredUser.getEmail());

                Map<String, Object> response = new HashMap<>();
                response.put("message", "Registration successful");
                response.put("token", token);
                response.put("email", registeredUser.getEmail());
                response.put("firstName", registeredUser.getFirstName());
                response.put("lastName", registeredUser.getLastName());
                response.put("userType", userType);

                return ResponseEntity.ok(response);
            } catch (Exception e) {
                System.err.println("Error during user registration: " + e.getMessage());
                if (e.getMessage().contains("JavaMailSender") ||
                        (e.getCause() != null && e.getCause().toString().contains("Mail"))) {
                    return ResponseEntity.ok(Map.of(
                            "message", "Registration successful but verification email could not be sent",
                            "email", user.getEmail()
                    ));
                }
                throw e;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("message", "Registration failed: " + e.getMessage()));
        }
    }

    @GetMapping("/confirm-account")
    public ResponseEntity<?> confirmAccount(@RequestParam String token) {
        try {
            User user = userService.confirmUser(token);
            return ResponseEntity.ok(Map.of("message", "Account confirmed successfully", "email", user.getEmail()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getEmail(),
                            loginRequest.getPassword()
                    )
            );

            CustomUserDetailsService.CustomUserDetails userDetails =
                    (CustomUserDetailsService.CustomUserDetails) authentication.getPrincipal();

            User user = userRepository.findById(userDetails.getUserId())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

            // Reset failed attempts on success
            user.resetFailedAttempts();
            userRepository.save(user);

            String token = jwtUtil.generateToken(user.getEmail());
            String userType = determineUserType(user.getRoles());

            return ResponseEntity.ok(Map.of(
                    "token", token,
                    "userId", user.getId(),
                    "userType", userType,
                    "email", user.getEmail(),
                    "firstName", user.getFirstName(),
                    "lastName", user.getLastName(),
                    "verified", user.isVerified()
            ));

        } catch (BadCredentialsException e) {
            handleFailedLoginAttempt(loginRequest.getEmail());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid email or password"));
        } catch (CustomUserDetailsService.AccountNotVerifiedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        } catch (CustomUserDetailsService.AccountLockedException e) {
            return ResponseEntity.status(HttpStatus.LOCKED)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Login failed: " + e.getMessage()));
        }
    }

    private void handleFailedLoginAttempt(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            user.incrementFailedAttempts();
            if (user.getFailedLoginAttempts() >= MAX_FAILED_ATTEMPTS) {
                user.setLockTime(LocalDateTime.now());
            }
            userRepository.save(user);
        });
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
        if (roles.contains(Role.ROLE_CLIENT)) {
            return "client";
        }
        return "unknown";
    }

    // Other methods remain the same...

    @Data
    static class LoginRequest {
        @NotBlank
        @Email
        private String email;

        @NotBlank
        @Size(min = 8)
        private String password;
    }




    // Other methods remain the same...


    @GetMapping("/oauth2-success")
    public ResponseEntity<?> oauthLoginSuccess(@AuthenticationPrincipal OAuth2User principal) {
        String email = principal.getAttribute("email");
        if (email != null) {
            User user = userService.findByEmail(email);
            String token = jwtUtil.generateToken(email);

            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("userType", determineUserType(user.getRoles()));
            response.put("email", user.getEmail());
            response.put("firstName", user.getFirstName());
            response.put("lastName", user.getLastName());

            return ResponseEntity.ok(response);
        }

        return ResponseEntity.badRequest().body(Map.of("error", "Failed to retrieve user information"));
    }



    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody User updatedUser, Principal principal) {
        try {
            String email = principal.getName(); // Get the current user's email from the security context
            User user = userService.updateProfile(email, updatedUser);
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}