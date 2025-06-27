package com.example.ExpedNow.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class SecurityService {
    public boolean canAccessAvailability(Authentication authentication, String userId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        CustomUserDetailsService.CustomUserDetails userDetails =
                (CustomUserDetailsService.CustomUserDetails) authentication.getPrincipal();

        // Admin أو Delivery Person يقدر يدير Schedule
        if (userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ADMIN") ||
                        a.getAuthority().equals("ROLE_DELIVERY_PERSON"))) {
            return true;
        }

        // اليوزر يقدر يدير Schedule تاعو الخاص
        return userDetails.getUserId().equals(userId);
    }
}