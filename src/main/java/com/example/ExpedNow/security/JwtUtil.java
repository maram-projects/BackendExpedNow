package com.example.ExpedNow.security;

import com.example.ExpedNow.models.Role;
import com.example.ExpedNow.models.User;
import com.example.ExpedNow.repositories.UserRepository;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.security.Key;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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

    // In your JwtUtils class, modify the generateToken method:
    public String generateToken(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        Claims claims = Jwts.claims().setSubject(email);

        // Add both the original roles and mapped roles for Spring Security
        List<String> roleNames = new ArrayList<>();

        for (Role role : user.getRoles()) {
            roleNames.add(role.name());

            // For INDIVIDUAL, ENTERPRISE roles, also add CLIENT role for Spring Security
            if (role == Role.ROLE_INDIVIDUAL || role == Role.ROLE_ENTERPRISE) {
                roleNames.add("CLIENT");
            }
            // For TEMPORARY, PROFESSIONAL roles, also add DELIVERY role
            else if (role == Role.ROLE_TEMPORARY || role == Role.ROLE_PROFESSIONAL) {
                roleNames.add("DELIVERY");
            }
            // No mapping needed for ADMIN - keep as is
        }

        claims.put("roles", roleNames);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(jwtSecretKey, SignatureAlgorithm.HS256)
                .compact();
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

    public String getEmailFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(jwtSecretKey)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }
}