package com.example.ExpedNow.controllers;

import com.example.ExpedNow.models.Role;
import com.example.ExpedNow.models.User;
import com.example.ExpedNow.security.JwtUtil;
import com.example.ExpedNow.services.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:4200")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final JwtUtil jwtUtil;

    // Constructor injection
    public AuthController(AuthenticationManager authenticationManager,
                          UserService userService,
                          JwtUtil jwtUtil) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
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
                    roles.add(Role.ROLE_ADMIN);
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
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        try {
            // Extract email and password from the request
            String email = credentials.get("email");
            String password = credentials.get("password");

            if (email == null || password == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Email and password are required"));
            }

            // Create authentication token
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(email, password);

            try {
                Authentication authentication = authenticationManager.authenticate(authToken);

                if (authentication.isAuthenticated()) {
                    User userDetails = userService.findByEmail(email);

                    if (!userDetails.isVerified()) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("error", "Email not verified. Please check your inbox."));
                    }

                    String token = jwtUtil.generateToken(email);

                    Map<String, Object> response = new HashMap<>();
                    response.put("token", token);
                    response.put("userType", determineUserType(userDetails.getRoles()));
                    response.put("email", userDetails.getEmail());
                    response.put("firstName", userDetails.getFirstName());
                    response.put("lastName", userDetails.getLastName());

                    return ResponseEntity.ok(response);
                } else {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Invalid email or password"));
                }
            } catch (BadCredentialsException e) {
                return ResponseEntity.status(401)
                        .body(Map.of("error", "Invalid email or password"));
            } catch (AuthenticationException e) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Authentication failed: " + e.getMessage()));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Login failed: " + e.getMessage()));
        }
    }

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