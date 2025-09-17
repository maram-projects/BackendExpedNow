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
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
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
    private final MongoTemplate mongoTemplate;
    @Autowired
    private EmailService emailService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    @Value("${spring.mail.enabled:false}")
    private boolean emailEnabled;

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
                           @Autowired(required = false) JavaMailSender mailSender,
                           VehicleRepository vehicleRepository,
                           MongoTemplate mongoTemplate,
                           PasswordResetTokenRepository passwordResetTokenRepository) {
        this.userRepository = userRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.vehicleRepository = vehicleRepository;
        this.mongoTemplate = mongoTemplate;
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

        if (user.getRoles() != null) {
            for (Role role : user.getRoles()) {
                authorities.add(new SimpleGrantedAuthority(role.name()));
            }
        }

        return authorities;
    }

    @Override
    public User registerUser(User user, Set<Role> roles) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setRoles(roles);
        user.setVerified(true);
        user.setDateOfRegistration(new Date());

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
    public List<User> findByRoles(Role role) {
        return List.of();
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

        user.setVerified(true);
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
            newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            newUser.setRoles(Set.of(Role.ROLE_CLIENT, Role.ROLE_INDIVIDUAL));
            newUser.setVerified(true);
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

    @Override
    public String getUserIdFromToken(String authHeader) {
        String token = authHeader.replace("Bearer ", "");

        Claims claims = Jwts.parserBuilder()
                .setSigningKey(jwtSecret.getBytes())
                .build()
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

    @Override
    public ResponseEntity<?> assignVehicle(String userId, String vehicleId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

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

    @Override
    public User findById(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + id));
    }

    @Override
    public User findByIdWithVehicle(String id) {
        Query query = new Query(Criteria.where("id").is(id));
        query.fields().include("assignedVehicle");

        User user = mongoTemplate.findOne(query, User.class);
        if (user == null) {
            throw new ResourceNotFoundException("User not found with ID: " + id);
        }
        return user;
    }

    @Override
    public List<UserDTO> findAvailableDrivers() {
        return userRepository.findByRolesContainingAndAssignedVehicleIdIsNull(String.valueOf(Role.ROLE_PROFESSIONAL))
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<User> findAll() {
        return userRepository.findAll();
    }

    @Override
    public User save(User user) {
        return userRepository.save(user);
    }

    @Override
    public void deleteById(String id) {
        // Implementation if needed
    }

    @Override
    public ResponseEntity<?> assignVehicleToUser(String userId, String vehicleId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

            boolean isDeliveryPerson = user.getRoles().stream()
                    .anyMatch(role -> role == Role.ROLE_DELIVERY_PERSON ||
                            role == Role.ROLE_PROFESSIONAL ||
                            role == Role.ROLE_TEMPORARY);

            if (!isDeliveryPerson) {
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
            User updatedUser = userRepository.save(user);

            vehicle.setAvailable(false);
            Vehicle updatedVehicle = vehicleRepository.save(vehicle);

            Map<String, Object> response = new HashMap<>();
            response.put("user", convertToDTO(updatedUser));
            response.put("vehicle", convertVehicleToDTO(updatedVehicle));

            return ResponseEntity.ok(response);
        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Collections.singletonMap("error", ex.getMessage()));
        } catch (Exception ex) {
            logger.error("Error assigning vehicle: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to assign vehicle"));
        }
    }

    public List<User> searchClients(String query) {
        String searchPattern = "%" + query + "%";
        return userRepository.findByClientAttributes(searchPattern);
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
    //hyy
    @Override
    public UserDTO findByAssignedVehicle(String vehicleId) {
        User user = userRepository.findByAssignedVehicleId(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("No user assigned to this vehicle"));

        if (user.getAssignedVehicleId() != null) {
            Vehicle vehicle = vehicleRepository.findById(user.getAssignedVehicleId())
                    .orElse(null);
            user.setAssignedVehicle(vehicle);
        }

        return convertToDTO(user);
    }

    private User unassignVehicleFromUserInternal(String userId) {
        // Your existing unassign logic here
        return null;
    }
    @Override
    public ResponseEntity<?> unassignVehicleFromUser(String userId, String vehicleId) {
        try {
            User user = unassignVehicleFromUser(userId); // Call your existing logic
            return ResponseEntity.ok().body(Collections.singletonMap("message", "Vehicle unassigned successfully"));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to unassign vehicle: " + ex.getMessage()));
        }
    }

    // Replace your unassignVehicleFromUser method in UserServiceImpl with this:

    public User unassignVehicleFromUser(String userId) {
        // Find the user
        User user = findById(userId);
        if (user == null) {
            throw new RuntimeException("User not found with id: " + userId);
        }

        // Get the assigned vehicle ID before clearing it
        String assignedVehicleId = user.getAssignedVehicleId();

        // Clear the vehicle assignment from user
        user.setAssignedVehicleId(null);
        user.setAssignedVehicle(null);

        // If there was an assigned vehicle, make it available again
        if (assignedVehicleId != null) {
            try {
                Optional<Vehicle> vehicleOptional = vehicleRepository.findById(assignedVehicleId);
                if (vehicleOptional.isPresent()) {
                    Vehicle vehicle = vehicleOptional.get();
                    vehicle.setAvailable(true);
                    vehicleRepository.save(vehicle); // Use vehicleRepository instead of vehicleService
                }
            } catch (Exception e) {
                // Log the error but don't fail the user update
                logger.error("Failed to update vehicle availability: " + e.getMessage(), e);
            }
        }

        // Save and return the updated user
        return save(user);
    }
    public UserDTO convertToDTO(User user) {
        if (user == null) {
            return null;
        }

        UserDTO dto = new UserDTO();

        dto.setId(user.getId());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setAddress(user.getAddress());
        dto.setDateOfRegistration(user.getDateOfRegistration());

        dto.setCompanyName(user.getCompanyName());
        dto.setBusinessType(user.getBusinessType());
        dto.setVatNumber(user.getVatNumber());
        dto.setBusinessPhone(user.getBusinessPhone());
        dto.setBusinessAddress(user.getBusinessAddress());
        dto.setDeliveryRadius(user.getDeliveryRadius());

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

        dto.setVerified(user.isVerified());
        dto.setEnabled(user.isEnabled());
        dto.setAvailable(user.isAvailable());
        dto.setApproved(user.isApproved());

        dto.setRating(user.getRating());
        dto.setCompletedDeliveries(user.getCompletedDeliveries());
        dto.setLastActive(user.getLastActive());
        dto.setSuccessScore(user.getSuccessScore());
        dto.setTotalDeliveries(user.getTotalDeliveries());
        dto.setAverageDeliveryTime(user.getAverageDeliveryTime());

        if (user.getRoles() != null) {
            Set<String> roleStrings = user.getRoles().stream()
                    .map(Role::name)
                    .collect(Collectors.toSet());
            dto.setRoles(roleStrings);
        }

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

        user.setId(dto.getId());
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setAddress(dto.getAddress());
        user.setDateOfRegistration(dto.getDateOfRegistration());

        user.setCompanyName(dto.getCompanyName());
        user.setBusinessType(dto.getBusinessType());
        user.setVatNumber(dto.getVatNumber());
        user.setBusinessPhone(dto.getBusinessPhone());
        user.setBusinessAddress(dto.getBusinessAddress());
        user.setDeliveryRadius(dto.getDeliveryRadius());

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

        user.setVerified(dto.isVerified());
        user.setEnabled(dto.isEnabled());
        user.setAvailable(dto.isAvailable());

        user.setRating(dto.getRating());
        user.setCompletedDeliveries(dto.getCompletedDeliveries());
        user.setLastActive(dto.getLastActive());
        user.setSuccessScore(dto.getSuccessScore());
        user.setTotalDeliveries(dto.getTotalDeliveries());
        user.setAverageDeliveryTime(dto.getAverageDeliveryTime());

        if (dto.getRoles() != null) {
            Set<Role> roles = dto.getRoles().stream()
                    .map(Role::valueOf)
                    .collect(Collectors.toSet());
            user.setRoles(roles);
        }

        return user;
    }

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

    @Override
    public void createPasswordResetToken(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        String token = UUID.randomUUID().toString();

        user.setResetToken(token);
        user.setResetTokenExpiry(LocalDateTime.now().plusHours(24));

        userRepository.save(user);
        sendPasswordResetEmail(user, token);
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
        user.setVerified(true);

        if (mailSender != null) {
            sendApprovalEmail(user);
        }

        return userRepository.save(user);
    }

    @Override
    public User disableUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setEnabled(false);
        return userRepository.save(user);
    }

    @Override
    public void rejectUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (mailSender != null) {
            sendRejectionEmail(user);
        }

        userRepository.delete(user);
    }

    public void sendPasswordResetEmail(User user, String token) {
        try {
            emailService.sendPasswordResetEmail(user, token);
        } catch (Exception e) {
            logger.error("Failed to send password reset email", e);
            String resetLink = "http://localhost:4200/reset-password?token=" + token;
            System.out.println("\n=== DEV MODE: Password reset link ===");
            System.out.println("Email: " + user.getEmail());
            System.out.println("Link: " + resetLink);
            System.out.println("==============================\n");
        }
    }

    private void sendApprovalEmail(User user) {
        try {
            emailService.sendAccountApprovalEmail(user);
        } catch (Exception e) {
            logger.error("Failed to send approval email", e);
        }
    }

    private void sendRejectionEmail(User user) {
        try {
            emailService.sendAccountRejectionEmail(user, "Your account did not meet our requirements.");
        } catch (Exception e) {
            logger.error("Failed to send rejection email", e);
        }
    }

    @Override
    public long countActiveUsers() {
        return userRepository.countByEnabledTrueAndApprovedTrue();
    }

    @Override
    public long countTotalUsers() {
        return userRepository.count();
    }

    @Override
    public long countInactiveUsers() {
        return userRepository.countByEnabledFalseOrApprovedFalse();
    }

    @Override
    public Map<String, Long> countUsersByRole() {
        Map<String, Long> counts = new HashMap<>();
        for (Role role : Role.values()) {
            counts.put(role.name(), userRepository.countByRolesContains(role));
        }
        return counts;
    }

    /**
     * @param email 
     * @return
     * @throws UsernameNotFoundException
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return null;
    }

    // REMOVE THE DUPLICATE loadUserByUsername METHOD
    // Keep only the findByUsername method which is required by the interface
    @Override
    public User findByUsername(String username) {
        return userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + username));
    }

    // Custom exceptions
    public static class AccountNotApprovedException extends RuntimeException {
        public AccountNotApprovedException(String message) {
            super(message);
        }
    }
}