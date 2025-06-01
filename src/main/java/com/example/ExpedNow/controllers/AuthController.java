package com.example.ExpedNow.controllers;

import com.example.ExpedNow.dto.LoginRequest;
import com.example.ExpedNow.dto.UserDTO;
import com.example.ExpedNow.exception.ResourceNotFoundException;
import com.example.ExpedNow.models.enums.Role;
import com.example.ExpedNow.models.User;
import com.example.ExpedNow.repositories.UserRepository;
import com.example.ExpedNow.security.CustomUserDetailsService;
import com.example.ExpedNow.security.JwtUtil;
import com.example.ExpedNow.services.core.impl.UserServiceImpl;
import com.example.ExpedNow.services.core.impl.VehicleServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final UserServiceImpl userService;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Autowired
    private VehicleServiceImpl vehicleService;

    // Inject authentication manager directly instead of through SecurityConfig

    public AuthController(AuthenticationManager authenticationManager,
                          UserServiceImpl userService,
                          UserRepository userRepository,
                          JwtUtil jwtUtil,
                          PasswordEncoder passwordEncoder,
                          JavaMailSender mailSender) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user,
                                      @RequestParam String userType) {
        try {
            // Validate password match
            if (!user.getPassword().equals(user.getConfirmPassword())) {
                return ResponseEntity.badRequest().body(Map.of("message", "Passwords do not match"));
            }

            if (user.getEmail() == null || user.getPassword() == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Email and password are required"));
            }

            if (userService.isEmailTaken(user.getEmail())) {
                return ResponseEntity.badRequest().body(Map.of("message", "Email already exists"));
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
                    // Auto-verify and auto-approve admin accounts
                    user.setVerified(true);
                    user.setApproved(true);
                    user.setEnabled(true);
                    break;

                default:
                    return ResponseEntity.badRequest().body(Map.of("message", "Invalid user type"));
            }

            // Set approved status - only admins are auto-approved
            boolean isAdmin = roles.contains(Role.ADMIN);
            user.setApproved(isAdmin);
            user.setEnabled(isAdmin);

            User registeredUser = userService.registerUser(user, roles);

            // For non-admin users, don't generate token yet
            Map<String, Object> response = new HashMap<>();
            response.put("message", isAdmin ? "Registration successful" : "Registration successful. Waiting for admin approval.");

            if (isAdmin) {
                String token = jwtUtil.generateToken(registeredUser.getId());
                response.put("token", token);
                response.put("userId", registeredUser.getId());
                response.put("email", registeredUser.getEmail());
                response.put("firstName", registeredUser.getFirstName());
                response.put("lastName", registeredUser.getLastName());
                response.put("userType", userType);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Registration failed: " + e.getMessage()));
        }
    }


    // Add these new endpoints for password reset
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        if (email == null || email.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
        }

        try {
            Optional<User> userOptional = userRepository.findByEmail(email);
            if (userOptional.isEmpty()) {
                // For security reasons, don't reveal if the email exists or not
                return ResponseEntity.ok(Map.of("message", "If an account exists, a password reset email has been sent"));
            }

            User user = userOptional.get();

            // Generate token
            String resetToken = UUID.randomUUID().toString();
            user.setResetToken(resetToken);
            user.setTokenExpiryDate(LocalDateTime.now().plusHours(1)); // Token valid for 1 hour
            userRepository.save(user);

            // Send email
            String resetLink = frontendUrl + "/reset-password?token=" + resetToken;

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(user.getEmail());
            message.setSubject("Password Reset Request");
            message.setText("To reset your password, click the link below:\n" + resetLink +
                    "\n\nThis link will expire in 1 hour." +
                    "\n\nIf you didn't request a password reset, please ignore this email.");

            mailSender.send(message);

            return ResponseEntity.ok(Map.of("message", "Password reset email sent if account exists"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process password reset: " + e.getMessage()));
        }
    }
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> request) {
        try {
            String token = request.get("token");
            String newPassword = request.get("newPassword");
            String confirmPassword = request.get("confirmPassword");

            if (token == null || token.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Reset token is required"));
            }

            if (newPassword == null || newPassword.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "New password is required"));
            }

            if (!newPassword.equals(confirmPassword)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Passwords do not match"));
            }

            Optional<User> userOptional = userRepository.findByResetToken(token);
            if (userOptional.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired reset token"));
            }

            User user = userOptional.get();

            // Check if token is expired
            if (user.getTokenExpiryDate() == null || user.getTokenExpiryDate().isBefore(LocalDateTime.now())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Reset token has expired"));
            }

            // Update password
            user.setPassword(passwordEncoder.encode(newPassword));
            user.setResetToken(null);
            user.setTokenExpiryDate(null);
            userRepository.save(user);

            return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to reset password: " + e.getMessage()));
        }
    }

    // Add these new endpoints for admin approval
    @PreAuthorize("hasAuthority('ADMIN')")
    @GetMapping("/pending-approvals")
    public ResponseEntity<List<User>> getPendingApprovals() {
        return ResponseEntity.ok(userService.findByApprovedFalse());
    }



    @PreAuthorize("hasAuthority('ADMIN')")
    @PostMapping("/reject-user/{userId}")
    public ResponseEntity<?> rejectUser(@PathVariable String userId) {
        try {
            userService.rejectUser(userId);
            return ResponseEntity.ok(Map.of("message", "User rejected successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
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

            User user = userRepository.findByEmail(loginRequest.getEmail())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

            user.resetFailedAttempts();
            userRepository.save(user);

            String token = jwtUtil.generateToken(user.getId());
            String userType = determineUserType(user.getRoles());

            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("token", token);
            responseMap.put("userId", user.getId());
            responseMap.put("userType", userType);
            responseMap.put("email", user.getEmail());
            responseMap.put("firstName", user.getFirstName());
            responseMap.put("lastName", user.getLastName());
            responseMap.put("verified", user.isVerified());

            if (user.getRoles().contains(Role.ROLE_DELIVERY_PERSON)) {
                responseMap.put("vehicleType", user.getVehicleType());
                responseMap.put("assignedVehicleId", user.getAssignedVehicleId());
            }

            return ResponseEntity.ok(responseMap);

        } catch (BadCredentialsException e) {
            handleFailedLoginAttempt(loginRequest.getEmail());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid email or password"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Login failed: " + e.getMessage()));
        }
    }

    @ControllerAdvice
    public class GlobalExceptionHandler {

        @ExceptionHandler(ResourceNotFoundException.class)
        public ResponseEntity<?> handleResourceNotFound(ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", ex.getMessage()));
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<?> handleGlobalException(Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "An unexpected error occurred"));
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