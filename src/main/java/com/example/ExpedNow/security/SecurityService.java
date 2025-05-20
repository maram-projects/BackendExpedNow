package com.example.ExpedNow.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class SecurityService {

    public boolean canAccessAvailability(Authentication authentication, String userId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        CustomUserDetailsService.CustomUserDetails userDetails = (CustomUserDetailsService.CustomUserDetails) authentication.getPrincipal();

        // Admin can access any schedule
        if (userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ADMIN") ||
                        a.getAuthority().equals("ROLE_ADMIN"))) {
            return true;
        }

        // Users can access their own schedule
        return userDetails.getUserId().equals(userId);
    }
}