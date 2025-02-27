package com.example.ExpedNow.security;

import com.example.ExpedNow.models.Role;
import com.example.ExpedNow.models.User;
import com.example.ExpedNow.repositories.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

@Service("customUserDetailsService")
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_TIME_MINUTES = 30;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        checkAccountStatus(user);

        return new CustomUserDetails(
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

    // Update your CustomUserDetailsService's getAuthorities method
    private Collection<? extends GrantedAuthority> getAuthorities(User user) {
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            return Collections.emptyList();
        }

        Collection<GrantedAuthority> authorities = new ArrayList<>();

        for (Role role : user.getRoles()) {
            // For ADMIN role, add both ADMIN and ROLE_ADMIN authorities
            if (role == Role.ADMIN) {
                authorities.add(new SimpleGrantedAuthority("ADMIN"));
                authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            }
            // For other roles that already have ROLE_ prefix, use as is
            else if (role.name().startsWith("ROLE_")) {
                authorities.add(new SimpleGrantedAuthority(role.name()));
                // Also add the non-prefixed version for consistency
                authorities.add(new SimpleGrantedAuthority(role.name().substring(5)));
            }
            // For any other roles without prefix, add both versions
            else {
                authorities.add(new SimpleGrantedAuthority(role.name()));
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
            }
        }

        return authorities;
    }

    private void checkAccountStatus(User user) {
        if (!user.isVerified()) {
            throw new AccountNotVerifiedException("Account not verified. Please check your email.");
        }

        if (user.getLockTime() != null &&
                ChronoUnit.MINUTES.between(user.getLockTime(), LocalDateTime.now()) < LOCK_TIME_MINUTES) {
            throw new AccountLockedException("Account temporarily locked. Try again later.");
        }
    }

    // Custom UserDetails implementation
    public static class CustomUserDetails implements UserDetails {
        private final String userId;
        private final String username;
        private final String password;
        private final Collection<? extends GrantedAuthority> authorities;
        private final boolean enabled;
        private final boolean verified;
        private final int failedAttempts;
        private final LocalDateTime lockTime;

        public CustomUserDetails(String userId, String username, String password,
                                 Collection<? extends GrantedAuthority> authorities,
                                 boolean enabled, boolean verified,
                                 int failedAttempts, LocalDateTime lockTime) {
            this.userId = userId;
            this.username = username;
            this.password = password;
            this.authorities = authorities;
            this.enabled = enabled;
            this.verified = verified;
            this.failedAttempts = failedAttempts;
            this.lockTime = lockTime;
        }

        public String getUserId() {
            return userId;
        }

        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            return authorities;
        }

        @Override
        public String getPassword() {
            return password;
        }

        @Override
        public String getUsername() {
            return username;
        }

        @Override
        public boolean isAccountNonExpired() {
            return true;
        }

        @Override
        public boolean isAccountNonLocked() {
            return lockTime == null ||
                    ChronoUnit.MINUTES.between(lockTime, LocalDateTime.now()) >= LOCK_TIME_MINUTES;
        }

        @Override
        public boolean isCredentialsNonExpired() {
            return true;
        }

        @Override
        public boolean isEnabled() {
            return enabled && verified;
        }
    }

    // Custom exceptions
    public static class AccountNotVerifiedException extends RuntimeException {
        public AccountNotVerifiedException(String message) {
            super(message);
        }
    }

    public static class AccountLockedException extends RuntimeException {
        public AccountLockedException(String message) {
            super(message);
        }
    }
}