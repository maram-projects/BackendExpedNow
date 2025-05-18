package com.example.ExpedNow.security;

import com.example.ExpedNow.models.enums.Role;
import com.example.ExpedNow.models.User;
import com.example.ExpedNow.repositories.UserRepository;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class JwtUtil {
    private final Key jwtSecretKey;
    private final long jwtExpirationMs;
    private final UserRepository userRepository;

    public JwtUtil(@Value("${jwt.secret}") String secretKey,
                   @Value("${jwt.expirationMs}") long expirationMs,
                   UserRepository userRepository) {
        this.jwtSecretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));
        this.jwtExpirationMs = expirationMs;
        this.userRepository = userRepository;
    }

    public String generateToken(String userId) {
        User user = userRepository.findById(userId) // Ici findByEmail -> findById
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

        return Jwts.builder()
                .setSubject(userId)
                .claim("email", user.getEmail())
                .claim("roles", getMappedRoles(user.getRoles()))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(jwtSecretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    private List<String> getMappedRoles(Set<Role> roles) {
        return roles.stream()
                .map(role -> {
                    // Keep ADMIN role as "ADMIN" without conversion
                    if (role == Role.ADMIN) {
                        return "ADMIN";
                    }

                    // Other role mappings
                    switch (role) {
                        case ROLE_INDIVIDUAL:
                        case ROLE_ENTERPRISE:
                            return "CLIENT";
                        case ROLE_TEMPORARY:
                        case ROLE_PROFESSIONAL:
                            return "DELIVERY";
                        default:
                            return role.name();
                    }
                })
                .collect(Collectors.toList());
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(jwtSecretKey)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    public String getUserIdFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(jwtSecretKey)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    public String getEmailFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(jwtSecretKey)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .get("email", String.class);
    }
}