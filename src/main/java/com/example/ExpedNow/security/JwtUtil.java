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
import java.util.Date;
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

    public String generateToken(String email) {
        // Instead of using UserService, use UserRepository directly
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        Claims claims = Jwts.claims().setSubject(email);
        claims.put("roles", user.getRoles().stream()
                .map(Role::name)
                .collect(Collectors.toList()));

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