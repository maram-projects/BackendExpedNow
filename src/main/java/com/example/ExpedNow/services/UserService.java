package com.example.ExpedNow.services;

import com.example.ExpedNow.models.Role;
import com.example.ExpedNow.models.User;
import com.example.ExpedNow.models.VerificationToken;
import com.example.ExpedNow.repositories.UserRepository;
import com.example.ExpedNow.repositories.VerificationTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;

    public UserService(UserRepository userRepository,
                       VerificationTokenRepository verificationTokenRepository,
                       PasswordEncoder passwordEncoder,
                       @Autowired(required = false) JavaMailSender mailSender) {
        this.userRepository = userRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
    }


    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
    }

    public User registerUser(User user, Set<Role> roles) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setRoles(roles);
        user.setVerified(false); // Set user as unverified by default

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

    public boolean isEmailTaken(String email) {
        return userRepository.existsByEmail(email);
    }

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
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setVerified(true); // Mark user as verified
        userRepository.save(user);

        verificationTokenRepository.delete(verificationToken);
        logger.info("User verified successfully: {}", user.getEmail());

        return user;
    }

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

    public User updateProfile(String email, User updatedUser) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setFirstName(updatedUser.getFirstName());
        user.setLastName(updatedUser.getLastName());
        user.setPhone(updatedUser.getPhone());
        user.setAddress(updatedUser.getAddress());

        return userRepository.save(user);
    }
}