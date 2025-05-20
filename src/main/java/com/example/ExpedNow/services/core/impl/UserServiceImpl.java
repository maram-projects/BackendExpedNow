package com.example.ExpedNow.services.core.impl;

import com.example.ExpedNow.dto.UserDTO;
import com.example.ExpedNow.dto.VehicleDTO;
import com.example.ExpedNow.models.User;
import com.example.ExpedNow.models.Vehicle;
import com.example.ExpedNow.models.VerificationToken;
import com.example.ExpedNow.models.enums.Role;
import com.example.ExpedNow.repositories.PasswordResetTokenRepository;
import com.example.ExpedNow.repositories.UserRepository;
import com.example.ExpedNow.repositories.VehicleRepository;
import com.example.ExpedNow.repositories.VerificationTokenRepository;
import com.example.ExpedNow.security.CustomUserDetailsService;
import com.example.ExpedNow.services.core.UserServiceInterface;
import com.example.ExpedNow.exception.ResourceNotFoundException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;


@Service
@Primary
public class UserServiceImpl implements UserServiceInterface {
    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);
    private String id;
    private final UserRepository userRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final VehicleRepository vehicleRepository;

    private final PasswordResetTokenRepository passwordResetTokenRepository;


    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_TIME_MINUTES = 30;
    public String getId() {
        return id;
    }
    @Value("${app.jwt.secret:G7ZPaRwhmVa8yQ+NjJv5rjMczdbNLCCHsVt0k36bH+4=}")
    private String jwtSecret;

    @PostConstruct
    public void init() {
        logger.debug("JWT Secret: {}", jwtSecret);
    }

    public UserServiceImpl(UserRepository userRepository,
                           VerificationTokenRepository verificationTokenRepository,
                           PasswordEncoder passwordEncoder,
                           @Autowired(required = false) JavaMailSender mailSender, VehicleRepository vehicleRepository, PasswordResetTokenRepository passwordResetTokenRepository) {
        this.userRepository = userRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.vehicleRepository = vehicleRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }

    @Override
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
    }



    @Override
    public Collection<GrantedAuthority> getUserAuthorities(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        Collection<GrantedAuthority> authorities = new ArrayList<>();

        // Convert user roles to Spring Security authorities
        if (user.getRoles() != null) {
            for (Role role : user.getRoles()) {
                authorities.add(new SimpleGrantedAuthority(role.name()));
            }
        }

        return authorities;
    }

    @Override
    public User registerUser(User user, Set<Role> roles) {
        // Check if email already exists
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        // Set user properties
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setRoles(roles);
        user.setVerified(true); // Automatically mark as verified
        user.setDateOfRegistration(new Date());

        // Save and return the user (no verification token creation)
        return userRepository.save(user);
    }

    @Override
    public boolean isEmailTaken(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    public void sendVerificationEmail(User user, String token) {
        if (mailSender == null) {
            throw new RuntimeException("JavaMailSender is not configured");
        }

        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setTo(user.getEmail());
            mailMessage.setSubject("Complete Registration!");
            mailMessage.setText("To confirm your account, please click here: "
                    + "http://localhost:8080/api/auth/confirm-account?token=" + token);
            mailSender.send(mailMessage);
            logger.info("Verification email sent to: {}", user.getEmail());
        } catch (Exception e) {
            logger.error("Failed to send verification email: " + e.getMessage(), e);
            throw new RuntimeException("Failed to send verification email: " + e.getMessage(), e);
        }
    }

    @Override
    public User confirmUser(String token) {
        Optional<VerificationToken> verificationTokenOptional = verificationTokenRepository.findByToken(token);
        if (verificationTokenOptional.isEmpty()) {
            throw new RuntimeException("Invalid token");
        }

        VerificationToken verificationToken = verificationTokenOptional.get();
        if (verificationToken.getExpiryDate().before(new Date())) {
            throw new RuntimeException("Token has expired");
        }

        User user = userRepository.findById(verificationToken.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setVerified(true); // Mark user as verified
        userRepository.save(user);

        verificationTokenRepository.delete(verificationToken);
        logger.info("User verified successfully: {}", user.getEmail());

        return user;
    }

    @Override
    public User processOAuth2User(String email, String name) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isPresent()) {
            User existingUser = userOptional.get();
            logger.info("Existing OAuth2 user logged in: {}", email);
            return existingUser;
        } else {
            User newUser = new User();
            newUser.setEmail(email);
            newUser.setFirstName(name);
            // Generate a random password for OAuth2 users
            newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            newUser.setRoles(Set.of(Role.ROLE_CLIENT, Role.ROLE_INDIVIDUAL));
            newUser.setVerified(true); // OAuth2 users are considered verified
            newUser.setDateOfRegistration(new Date());
            User savedUser = userRepository.save(newUser);
            logger.info("New OAuth2 user registered: {}", email);
            return savedUser;
        }
    }

    @Override
    public User updateProfile(String email, User updatedUser) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setFirstName(updatedUser.getFirstName());
        user.setLastName(updatedUser.getLastName());
        user.setPhone(updatedUser.getPhone());
        user.setAddress(updatedUser.getAddress());

        return userRepository.save(user);
    }

    // In UserServiceImpl.java - Update getUserIdFromToken
    @Override
    public String getUserIdFromToken(String authHeader) {
        String token = authHeader.replace("Bearer ", "");

        Claims claims = Jwts.parser()
                .setSigningKey(jwtSecret)
                .parseClaimsJws(token)
                .getBody();

        String userId = claims.getSubject();
        logger.info("Extracted user ID from token: {}", userId);

        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with ID from token: " + userId);
        }

        return userId;
    }


    @Override
    public String getEmailFromToken(String authHeader) {
        return "";
    }

    @Override
    public User updateAvailability(String userId, boolean available) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setAvailable(available);
        user.setLastActive(new Date());

        return userRepository.save(user);
    }

    // In UserServiceImpl.java
    @Override
    public ResponseEntity<?> assignVehicle(String userId, String vehicleId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            // Check if user is a delivery person
            if (!user.getRoles().contains(Role.ROLE_DELIVERY_PERSON)) {
                return ResponseEntity.badRequest()
                        .body(Collections.singletonMap("error", "User is not a delivery person"));
            }

            Vehicle vehicle = vehicleRepository.findById(vehicleId)
                    .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));

            user.setAssignedVehicleId(vehicle.getId());
            vehicle.setAvailable(false);

            userRepository.save(user);
            vehicleRepository.save(vehicle);

            return ResponseEntity.ok().body(Collections.singletonMap("message", "Vehicle assigned successfully"));
        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Collections.singletonMap("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError()
                    .body(Collections.singletonMap("error", "Error assigning vehicle: " + ex.getMessage()));
        }
    }
    // In UserService.java
    @Override
    public List<User> getAllDeliveryPersons() {
        return userRepository.findAllByRolesContaining(Role.ROLE_DELIVERY_PERSON)
                .stream()
                .peek(user -> {
                    if (user.getAssignedVehicleId() != null) {
                        vehicleRepository.findById(user.getAssignedVehicleId())
                                .ifPresent(user::setAssignedVehicle);
                    }
                })
                .collect(Collectors.toList());
    }

    // In UserServiceImpl.java
    @Override
    public User findById(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + id));
    }

    @Override
    public List<UserDTO> findAvailableDrivers() {
        // Use the Role enum instead of a string
        return userRepository.findByRolesContainingAndAssignedVehicleIdIsNull(String.valueOf(Role.ROLE_PROFESSIONAL))
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<User> findAll() {
        return userRepository.findAll(); // Utilise le repository pour récupérer tous les utilisateurs
    }

    @Override
    public User save(User user) {
        return null;
    }

    @Override
    public void deleteById(String id) {

    }

    @Override
    public ResponseEntity<?> assignVehicleToUser(String userId, String vehicleId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

            if (!user.getRoles().contains(Role.ROLE_DELIVERY_PERSON)) {
                return ResponseEntity.badRequest()
                        .body(Collections.singletonMap("error", "User is not a delivery person"));
            }

            Vehicle vehicle = vehicleRepository.findById(vehicleId)
                    .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with ID: " + vehicleId));

            if (!vehicle.isAvailable()) {
                return ResponseEntity.badRequest()
                        .body(Collections.singletonMap("error", "Vehicle is already assigned"));
            }

            user.setAssignedVehicleId(vehicleId);
            vehicle.setAvailable(false);

            userRepository.save(user);
            vehicleRepository.save(vehicle);

            return ResponseEntity.ok()
                    .body(Collections.singletonMap("message", "Vehicle assigned successfully"));
        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Collections.singletonMap("error", ex.getMessage()));
        } catch (Exception ex) {
            logger.error("Error assigning vehicle: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to assign vehicle"));
        }
    }



    @Override
    public UserDTO getUserByVehicle(String vehicleId) {
        return userRepository.findByAssignedVehicleId(vehicleId)
                .map(this::convertToDTO)
                .orElseThrow(() -> new ResourceNotFoundException("No user assigned to vehicle ID: " + vehicleId));
    }
    @Override
    public List<UserDTO> getAvailableDrivers() {
        return userRepository.findByRolesContainingAndAssignedVehicleIdIsNull(String.valueOf(Role.ROLE_DELIVERY_PERSON))
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    @Override
    public UserDTO findByAssignedVehicle(String vehicleId) {
        User user = userRepository.findByAssignedVehicleId(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("No user assigned to this vehicle"));

        // Load vehicle details if needed
        if (user.getAssignedVehicleId() != null) {
            Vehicle vehicle = vehicleRepository.findById(user.getAssignedVehicleId())
                    .orElse(null);
            user.setAssignedVehicle(vehicle);
        }

        return convertToDTO(user);
    }
    @Override
    public ResponseEntity<?> unassignVehicleFromUser(String userId, String vehicleId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            Vehicle vehicle = vehicleRepository.findById(vehicleId)
                    .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));

            // تحقق إذا كانت المركبة معينة لهذا المستخدم
            if (!vehicleId.equals(user.getAssignedVehicleId())) {
                return ResponseEntity.badRequest().body("This vehicle is not assigned to the user");
            }

            // إلغاء التعيين
            user.setAssignedVehicleId(null);
            vehicle.setAvailable(true);

            userRepository.save(user);
            vehicleRepository.save(vehicle);

            return ResponseEntity.ok().body("Vehicle unassigned successfully");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error unassigning vehicle: " + e.getMessage());
        }
    }
    public UserDTO convertToDTO(User user) {
        if (user == null) {
            return null;
        }

        UserDTO dto = new UserDTO();

        // Basic user info
        dto.setId(user.getId());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setAddress(user.getAddress());
        dto.setDateOfRegistration(user.getDateOfRegistration());

        // Enterprise fields
        dto.setCompanyName(user.getCompanyName());
        dto.setBusinessType(user.getBusinessType());
        dto.setVatNumber(user.getVatNumber());
        dto.setBusinessPhone(user.getBusinessPhone());
        dto.setBusinessAddress(user.getBusinessAddress());
        dto.setDeliveryRadius(user.getDeliveryRadius());

        // Vehicle fields
        dto.setVehicleType(user.getVehicleType());
        dto.setVehicleBrand(user.getVehicleBrand());
        dto.setVehicleModel(user.getVehicleModel());
        dto.setVehiclePlateNumber(user.getVehiclePlateNumber());
        dto.setVehicleColor(user.getVehicleColor());
        dto.setVehicleYear(user.getVehicleYear());
        dto.setVehicleCapacityKg(user.getVehicleCapacityKg());
        dto.setVehicleVolumeM3(user.getVehicleVolumeM3());
        dto.setVehicleHasFridge(user.isVehicleHasFridge());
        dto.setVehiclePhotoUrl(user.getVehiclePhotoUrl());
        dto.setVehicleInsuranceExpiry(user.getVehicleInsuranceExpiry());
        dto.setVehicleInspectionExpiry(user.getVehicleInspectionExpiry());
        dto.setAssignedVehicleId(user.getAssignedVehicleId());

        // Professional fields
        dto.setDriverLicenseNumber(user.getDriverLicenseNumber());
        dto.setDriverLicenseCategory(user.getDriverLicenseCategory());
        dto.setDriverLicenseIssueDate(user.getDriverLicenseIssueDate());
        dto.setDriverLicenseExpiryDate(user.getDriverLicenseExpiryDate());
        dto.setIdentityPhotoUrl(user.getIdentityPhotoUrl());
        dto.setCriminalRecordDocumentUrl(user.getCriminalRecordDocumentUrl());
        dto.setMedicalCertificateUrl(user.getMedicalCertificateUrl());
        dto.setPreferredZones(user.getPreferredZones());
        dto.setAvailabilitySchedule(user.getAvailabilitySchedule());
        dto.setHasCompanyAffiliation(user.isHasCompanyAffiliation());

        // Account status
        dto.setVerified(user.isVerified());
        dto.setEnabled(user.isEnabled());
        dto.setAvailable(user.isAvailable());

        // Performance metrics
        dto.setRating(user.getRating());
        dto.setCompletedDeliveries(user.getCompletedDeliveries());
        dto.setLastActive(user.getLastActive());
        dto.setSuccessScore(user.getSuccessScore());
        dto.setTotalDeliveries(user.getTotalDeliveries());
        dto.setAverageDeliveryTime(user.getAverageDeliveryTime());

        // Roles
        if (user.getRoles() != null) {
            Set<String> roleStrings = user.getRoles().stream()
                    .map(Role::name)
                    .collect(Collectors.toSet());
            dto.setRoles(roleStrings);
        }
        // Password should not be set in DTO for security reasons

        // Convert assigned vehicle if present
        if (user.getAssignedVehicle() != null) {
            dto.setAssignedVehicle(convertVehicleToDTO(user.getAssignedVehicle()));
        }

        return dto;
    }

    private User convertToEntity(UserDTO dto) {
        if (dto == null) {
            return null;
        }

        User user = new User();

        // Basic user info
        user.setId(dto.getId());
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setAddress(dto.getAddress());
        user.setDateOfRegistration(dto.getDateOfRegistration());

        // Enterprise fields
        user.setCompanyName(dto.getCompanyName());
        user.setBusinessType(dto.getBusinessType());
        user.setVatNumber(dto.getVatNumber());
        user.setBusinessPhone(dto.getBusinessPhone());
        user.setBusinessAddress(dto.getBusinessAddress());
        user.setDeliveryRadius(dto.getDeliveryRadius());

        // Vehicle fields
        user.setVehicleType(dto.getVehicleType());
        user.setVehicleBrand(dto.getVehicleBrand());
        user.setVehicleModel(dto.getVehicleModel());
        user.setVehiclePlateNumber(dto.getVehiclePlateNumber());
        user.setVehicleColor(dto.getVehicleColor());
        user.setVehicleYear(dto.getVehicleYear());
        user.setVehicleCapacityKg(dto.getVehicleCapacityKg());
        user.setVehicleVolumeM3(dto.getVehicleVolumeM3());
        user.setVehicleHasFridge(dto.isVehicleHasFridge());
        user.setVehiclePhotoUrl(dto.getVehiclePhotoUrl());
        user.setVehicleInsuranceExpiry(dto.getVehicleInsuranceExpiry());
        user.setVehicleInspectionExpiry(dto.getVehicleInspectionExpiry());
        user.setAssignedVehicleId(dto.getAssignedVehicleId());

        // Professional fields
        user.setDriverLicenseNumber(dto.getDriverLicenseNumber());
        user.setDriverLicenseCategory(dto.getDriverLicenseCategory());
        user.setDriverLicenseIssueDate(dto.getDriverLicenseIssueDate());
        user.setDriverLicenseExpiryDate(dto.getDriverLicenseExpiryDate());
        user.setIdentityPhotoUrl(dto.getIdentityPhotoUrl());
        user.setCriminalRecordDocumentUrl(dto.getCriminalRecordDocumentUrl());
        user.setMedicalCertificateUrl(dto.getMedicalCertificateUrl());
        user.setPreferredZones(dto.getPreferredZones());
        user.setAvailabilitySchedule(dto.getAvailabilitySchedule());
        user.setHasCompanyAffiliation(dto.isHasCompanyAffiliation());

        // Account status
        user.setVerified(dto.isVerified());
        user.setEnabled(dto.isEnabled());
        user.setAvailable(dto.isAvailable());

        // Performance metrics
        user.setRating(dto.getRating());
        user.setCompletedDeliveries(dto.getCompletedDeliveries());
        user.setLastActive(dto.getLastActive());
        user.setSuccessScore(dto.getSuccessScore());
        user.setTotalDeliveries(dto.getTotalDeliveries());
        user.setAverageDeliveryTime(dto.getAverageDeliveryTime());

        // Roles
        if (dto.getRoles() != null) {
            Set<Role> roles = dto.getRoles().stream()
                    .map(Role::valueOf)
                    .collect(Collectors.toSet());
            user.setRoles(roles);
        }
        // Password should be handled separately in a dedicated method

        return user;
    }

    // Add this method if you need to convert Vehicle to VehicleDTO
    private VehicleDTO convertVehicleToDTO(Vehicle vehicle) {
        if (vehicle == null) {
            return null;
        }

        VehicleDTO dto = new VehicleDTO();
        dto.setId(vehicle.getId());
        dto.setVehicleType(vehicle.getVehicleType());
        dto.setVehicleBrand(vehicle.getMake());
        dto.setVehicleModel(vehicle.getModel());
        dto.setVehicleYear(vehicle.getYear());
        dto.setVehiclePlateNumber(vehicle.getLicensePlate());
        dto.setVehicleCapacityKg(vehicle.getMaxLoad());
        dto.setVehiclePhotoUrl(vehicle.getPhotoPath());
        dto.setAvailable(vehicle.isAvailable());

        return dto;
    }

    // In UserServiceImpl.java

    // Add these new methods

    @Override
    public void createPasswordResetToken(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        String token = UUID.randomUUID().toString();

        user.setResetToken(token);
        user.setResetTokenExpiry(LocalDateTime.now().plusHours(24));

        userRepository.save(user);
        sendPasswordResetEmail(user, token); // Changed from email String to User object
    }


    @Override
    public boolean validatePasswordResetToken(String token) {
        User user = userRepository.findByResetToken(token)
                .orElseThrow(() -> new InvalidTokenException("Invalid token"));

        return user.getResetTokenExpiry().isAfter(LocalDateTime.now());
    }

    @Override
    public void resetPassword(String token, String newPassword) {
        User user = userRepository.findByResetToken(token)
                .orElseThrow(() -> new InvalidTokenException("Invalid token"));

        if (user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new ExpiredTokenException("Token expired");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
    }

    public class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message) {
            super(message);
        }
    }

    public class ExpiredTokenException extends RuntimeException {
        public ExpiredTokenException(String message) {
            super(message);
        }
    }

    @Override
    public List<User> findByApprovedFalse() {
        return userRepository.findByApprovedFalseAndEnabledFalse();
    }

    @Override
    public User approveUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setApproved(true);
        user.setEnabled(true);

        // Send approval email if needed
        if (mailSender != null) {
            sendApprovalEmail(user);
        }

        return userRepository.save(user);
    }

    @Override
    public void rejectUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Send rejection email if needed
        if (mailSender != null) {
            sendRejectionEmail(user);
        }

        userRepository.delete(user);
    }

    private void sendPasswordResetEmail(User user, String token) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("lamiahaded99@gmail.com"); // تأكد من تطابقه مع username
            message.setTo(user.getEmail());
            message.setSubject("Password Reset Request");
            message.setText("To reset your password, please click here: " +
                    "http://localhost:4200/reset-password?token=" + token);

            mailSender.send(message);
            logger.info("Password reset email sent to: {}", user.getEmail());
        } catch (MailException e) {
            logger.error("Failed to send password reset email", e);
            throw new RuntimeException("Failed to send email: " + e.getMessage());
        }
    }
    private void sendApprovalEmail(User user) {
        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setTo(user.getEmail());
        mailMessage.setSubject("Account Approved");
        mailMessage.setText("Your account has been approved. You can now login at: "
                + "http://localhost:4200/login");
        mailSender.send(mailMessage);
    }

    private void sendRejectionEmail(User user) {
        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setTo(user.getEmail());
        mailMessage.setSubject("Account Rejected");
        mailMessage.setText("Your account registration has been rejected. "
                + "Please contact support if you believe this is an error.");
        mailSender.send(mailMessage);
    }

    // Update the loadUserByUsername method in CustomUserDetailsService to check for approval
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        // Skip verification/approval checks for ADMIN users
        if (!user.getRoles().contains(Role.ADMIN)) {
            if (!user.isVerified()) {
                throw new CustomUserDetailsService.AccountNotVerifiedException("Account not verified. Please check your email.");
            }

            if (!user.isApproved()) {
                throw new AccountNotApprovedException("Account not approved by admin yet");
            }
        }

        checkAccountStatus(user);

        return new CustomUserDetailsService.CustomUserDetails(
                user.getId(),
                user.getEmail(),
                user.getPassword(),
                getAuthorities(user),
                user.isEnabled(),
                user.isVerified(),
                user.getFailedLoginAttempts(),
                user.getLockTime()
        );
    }
    private Collection<? extends GrantedAuthority> getAuthorities(User user) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();

        if (user.getRoles() != null) {
            for (Role role : user.getRoles()) {
                authorities.add(new SimpleGrantedAuthority(role.name()));
            }
        }

        return authorities;
    }
    // In UserServiceImpl.java
    private void checkAccountStatus(User user) {
        if (!user.isVerified()) {
            throw new CustomUserDetailsService.AccountNotVerifiedException("Account not verified");
        }

        if (user.getLockTime() != null &&
                ChronoUnit.MINUTES.between(user.getLockTime(), LocalDateTime.now()) < LOCK_TIME_MINUTES) {
            throw new CustomUserDetailsService.AccountLockedException("Account temporarily locked. Try again later.");
        }
    }
    // Add this exception class
    public static class AccountNotApprovedException extends RuntimeException {
        public AccountNotApprovedException(String message) {
            super(message);
        }
    }
}