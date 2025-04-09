package com.example.ExpedNow.services.core;

import com.example.ExpedNow.models.User;
import com.example.ExpedNow.models.enums.Role;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Set;

public interface UserServiceInterface {
    User findByEmail(String email);

    Collection<GrantedAuthority> getUserAuthorities(String userId);

    User registerUser(User user, Set<Role> roles);

    boolean isEmailTaken(String email);

    void sendVerificationEmail(User user, String token);

    User confirmUser(String token);

    User processOAuth2User(String email, String name);

    User updateProfile(String email, User updatedUser);

    String getUserIdFromToken(String authHeader);

    User updateAvailability(String userId, boolean available);
}