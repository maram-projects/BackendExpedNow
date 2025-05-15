package com.example.ExpedNow.controllers;

import com.example.ExpedNow.dto.AvailabilityDTO;
import com.example.ExpedNow.exception.ResourceNotFoundException;
import com.example.ExpedNow.models.AvailabilitySchedule;
import com.example.ExpedNow.models.User;
import com.example.ExpedNow.repositories.UserRepository;
import com.example.ExpedNow.security.CustomUserDetailsService;
import com.example.ExpedNow.services.core.AvailabilityServiceInterface;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/availability")
public class AvailabilityController {
    private static final Logger logger = LoggerFactory.getLogger(AvailabilityController.class);

    private final AvailabilityServiceInterface availabilityService;
    private final UserRepository userRepository;

    @Autowired
    public AvailabilityController(AvailabilityServiceInterface availabilityService, UserRepository userRepository) {
        this.availabilityService = availabilityService;
        this.userRepository = userRepository;
    }

    /**
     * Get the availability schedule for a user
     *
     * @param userId ID of the user to get schedule for
     * @return The user's availability schedule
     */
    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.id")
    public ResponseEntity<AvailabilityDTO> getSchedule(@PathVariable String userId) {
        logger.info("Fetching availability schedule for user: {}", userId);
        AvailabilityDTO schedule = availabilityService.getScheduleForUser(userId);
        return ResponseEntity.ok(schedule);
    }

    /**
     * Get current user's availability schedule
     *
     * @param authentication Current authenticated user
     * @return The user's availability schedule
     */
    @GetMapping("/me")
    public ResponseEntity<AvailabilityDTO> getMySchedule(Authentication authentication) {
        String userId = authentication.getName();
        logger.info("Fetching availability schedule for current user: {}", userId);
        AvailabilityDTO schedule = availabilityService.getScheduleForUser(userId);
        return ResponseEntity.ok(schedule);
    }

    /**
     * Save or update a complete availability schedule
     *
     * @param scheduleDTO The schedule to save
     * @param authentication Current authenticated user
     * @return The saved availability schedule
     */
    @PostMapping
    public ResponseEntity<?> saveSchedule(
            @Valid @RequestBody AvailabilityDTO scheduleDTO,
            Authentication authentication) {

        // Get current user details from authentication
        CustomUserDetailsService.CustomUserDetails userDetails =
                (CustomUserDetailsService.CustomUserDetails) authentication.getPrincipal();

        String currentUserId = userDetails.getUserId(); // Actual user ID from authentication
        String currentUserEmail = authentication.getName();

        logger.info("User {} (ID: {}) attempting to save schedule for user ID: {}",
                currentUserEmail, currentUserId, scheduleDTO.getUserId());

        // Check admin status using your role structure
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ADMIN") ||
                        a.getAuthority().equals("ROLE_ADMIN"));

        // Validate ownership or admin rights
        if (!isAdmin && !currentUserId.equals(scheduleDTO.getUserId())) {
            logger.warn("Forbidden: User {} (ID: {}) tried to update schedule for {}",
                    currentUserEmail, currentUserId, scheduleDTO.getUserId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("You can only update your own schedule. Your ID: " + currentUserId
                            + ", Requested ID: " + scheduleDTO.getUserId());
        }

        // Ensure non-admins can only modify their own schedule
        if (!isAdmin) {
            if (!currentUserId.equals(scheduleDTO.getUserId())) {
                logger.info("Correcting userId in schedule from {} to {}",
                        scheduleDTO.getUserId(), currentUserId);
                scheduleDTO.setUserId(currentUserId);
            }
        } else {
            // For admin operations, verify the target user exists
            if (!userRepository.existsById(scheduleDTO.getUserId())) {
                throw new ResourceNotFoundException("User not found with ID: " + scheduleDTO.getUserId());
            }
        }

        logger.info("Saving availability schedule for user: {}", scheduleDTO.getUserId());
        AvailabilitySchedule savedSchedule = availabilityService.saveSchedule(scheduleDTO);
        return ResponseEntity.ok(savedSchedule);
    }

    /**
     * Update availability for a specific day
     *
     * @param userId User ID
     * @param day Day of week
     * @param isWorking Whether the user is working on this day
     * @param startTime Start time (if working)
     * @param endTime End time (if working)
     * @param authentication Current authenticated user
     * @return The updated availability schedule
     */
    @PutMapping("/{userId}/day/{day}")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.id")
    public ResponseEntity<AvailabilitySchedule> updateDayAvailability(
            @PathVariable String userId,
            @PathVariable DayOfWeek day,
            @RequestParam boolean isWorking,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endTime,
            Authentication authentication) {

        logger.info("Updating availability for user: {}, day: {}, working: {}", userId, day, isWorking);

        // Get authenticated user ID for comparison
        String currentUserId = authentication.getName();

        // Additional safety check
        if (!currentUserId.equals(userId) && !authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            logger.warn("Forbidden: User {} tried to update schedule day for {}",
                    currentUserId, userId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        AvailabilitySchedule updatedSchedule = availabilityService.setDayAvailability(
                userId, day, isWorking, startTime, endTime);
        return ResponseEntity.ok(updatedSchedule);
    }

    /**
     * Check if a user is available at a specific time
     *
     * @param userId User ID
     * @param dateTime Date and time to check
     * @return True if the user is available, false otherwise
     */
    @GetMapping("/{userId}/check")
    public ResponseEntity<Boolean> checkAvailability(
            @PathVariable String userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTime) {

        logger.info("Checking availability for user: {} at time: {}", userId, dateTime);
        boolean isAvailable = availabilityService.isUserAvailableAt(userId, dateTime);
        return ResponseEntity.ok(isAvailable);
    }

    /**
     * Find all delivery persons available at a specific time
     *
     * @param dateTime Date and time to check
     * @return List of user IDs of available delivery persons
     */
    @GetMapping("/available-delivery-persons")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AGENT')")
    public ResponseEntity<List<String>> findAvailableDeliveryPersons(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTime) {

        logger.info("Finding available delivery persons at time: {}", dateTime);
        List<String> availablePersons = availabilityService.findAvailableDeliveryPersonsAt(dateTime);
        return ResponseEntity.ok(availablePersons);
    }

    @GetMapping("/admin/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AvailabilityDTO> getScheduleForAdmin(@PathVariable String userId) {
        logger.info("Admin fetching availability schedule for user: {}", userId);

        // Check if userId is an email and find the user id if it is
        if (userId.contains("@")) {
            Optional<User> user = userRepository.findByEmail(userId);
            if (user.isPresent()) {
                userId = user.get().getId();
                logger.info("Found user by email, using ID: {}", userId);
            }
        }

        AvailabilityDTO schedule = availabilityService.getScheduleForUser(userId);
        logger.info("Found schedule for user {}, id: {}", userId, schedule.getId());
        return ResponseEntity.ok(schedule);
    }

    // Add these admin-specific endpoints
    @PostMapping("/admin/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AvailabilitySchedule> createScheduleForUser(
            @PathVariable String userId,
            @RequestBody AvailabilityDTO scheduleDTO) {
        // Check if userId is an email and find the user if it is
        if (userId.contains("@")) {
            Optional<User> user = userRepository.findByEmail(userId);
            if (user.isPresent()) {
                userId = user.get().getId();
                logger.info("Found user by email, using ID: {}", userId);
            }
        }

        scheduleDTO.setUserId(userId);
        logger.info("Admin creating schedule for user: {}", userId);
        AvailabilitySchedule savedSchedule = availabilityService.saveSchedule(scheduleDTO);
        return ResponseEntity.ok(savedSchedule);
    }

    @PutMapping("/admin/{userId}/day/{day}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AvailabilitySchedule> adminUpdateDayAvailability(
            @PathVariable String userId,
            @PathVariable DayOfWeek day,
            @RequestParam boolean isWorking,
            @RequestParam(required = false) LocalTime startTime,
            @RequestParam(required = false) LocalTime endTime) {

        AvailabilitySchedule updatedSchedule = availabilityService.setDayAvailability(
                userId, day, isWorking, startTime, endTime);
        return ResponseEntity.ok(updatedSchedule);
    }
}