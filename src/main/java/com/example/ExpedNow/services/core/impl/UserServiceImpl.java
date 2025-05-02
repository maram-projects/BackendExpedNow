package com.example.ExpedNow.services.core.impl;

import com.example.ExpedNow.models.User;
import com.example.ExpedNow.models.VerificationToken;
import com.example.ExpedNow.models.enums.Role;
import com.example.ExpedNow.repositories.UserRepository;
import com.example.ExpedNow.repositories.VerificationTokenRepository;
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
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Primary
public class UserServiceImpl implements UserServiceInterface {
    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;

    @Value("${app.jwt.secret:G7ZPaRwhmVa8yQ+NjJv5rjMczdbNLCCHsVt0k36bH+4=}")
    private String jwtSecret;

    @PostConstruct
    public void init() {
        logger.debug("JWT Secret: {}", jwtSecret);
    }

    public UserServiceImpl(UserRepository userRepository,
                           VerificationTokenRepository verificationTokenRepository,
                           PasswordEncoder passwordEncoder,
                           @Autowired(required = false) JavaMailSender mailSender) {
        this.userRepository = userRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
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
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setRoles(roles);
        user.setVerified(false); // Set user as unverified by default
        user.setDateOfRegistration(new Date()); // Set registration date

        User registeredUser = userRepository.save(user);

        try {
            String token = UUID.randomUUID().toString();
            VerificationToken verificationToken = new VerificationToken();
            verificationToken.setToken(token);
            verificationToken.setUserId(registeredUser.getId());
            verificationToken.setExpiryDate(new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000));
            verificationTokenRepository.save(verificationToken);

            if (mailSender != null) {
                sendVerificationEmail(registeredUser, token);
            } else {
                logger.warn("JavaMailSender not configured. Skipping email verification.");
                // For development purposes, auto-verify the user
                registeredUser.setVerified(true);
                userRepository.save(registeredUser);
            }
        } catch (Exception e) {
            logger.error("Failed to process verification: " + e.getMessage(), e);
        }

        return registeredUser;
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

    @Override
    public String getUserIdFromToken(String authHeader) {
        // Extract token from Authorization header
        String token = authHeader.replace("Bearer ", "");

        // Parse the token and get user id from claims
        Claims claims = Jwts.parser()
                .setSigningKey(jwtSecret)
                .parseClaimsJws(token)
                .getBody();

        return claims.getSubject(); // Assuming subject is the user ID
    }

    @Override
    public User updateAvailability(String userId, boolean available) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setAvailable(available);
        user.setLastActive(new Date());

        return userRepository.save(user);
    }



}