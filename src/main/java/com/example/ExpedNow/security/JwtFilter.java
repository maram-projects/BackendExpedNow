package com.example.ExpedNow.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class JwtFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(JwtFilter.class);

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    public JwtFilter(JwtUtil jwtUtil, @Qualifier("customUserDetailsService") UserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        final String requestPath = request.getRequestURI();
        logger.debug("Processing request path: {}", requestPath);

        // Skip filter for public endpoints - including uploads and health check
        if (isPublicEndpoint(requestPath)) {
            logger.debug("Skipping JWT filter for public endpoint: {}", requestPath);
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // For protected endpoints, return 401 if no token is provided
            logger.debug("No valid Authorization header found for protected endpoint: {}", requestPath);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Missing or invalid token\"}");
            return;
        }

        String token = authHeader.substring(7);
        try {
            String email = jwtUtil.getEmailFromToken(token);

            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                if (jwtUtil.validateToken(token)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    logger.debug("Successfully authenticated user: {}", email);
                } else {
                    logger.warn("Invalid JWT token for email: {}", email);
                }
            }
        } catch (Exception e) {
            logger.error("Cannot set user authentication: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Invalid token\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPublicEndpoint(String requestPath) {
        // Static resources
        if (requestPath.startsWith("/uploads/")) {
            return true;
        }

        // Auth endpoints
        if (requestPath.startsWith("/api/auth/register") ||
                requestPath.startsWith("/api/auth/login") ||
                requestPath.startsWith("/api/auth/confirm-account") ||
                requestPath.startsWith("/api/auth/forgot-password") ||
                requestPath.startsWith("/api/auth/reset-password")) {
            return true;
        }

        // OAuth2 endpoints
        if (requestPath.startsWith("/oauth2/")) {
            return true;
        }

        // API documentation
        if (requestPath.startsWith("/v3/api-docs") ||
                requestPath.startsWith("/swagger-ui")) {
            return true;
        }

        // WebSocket
        if (requestPath.startsWith("/ws/")) {
            return true;
        }

        // AI health check
        if (requestPath.equals("/api/ai/health")) {
            return true;
        }

        // Pricing endpoints
        if (requestPath.startsWith("/api/pricing/")) {
            return true;
        }

        // Public AI chat
        if (requestPath.equals("/api/ai/chat/public")) {
            return true;
        }

        // WebSocket related paths
        if (requestPath.startsWith("/topic/") ||
                requestPath.startsWith("/app/") ||
                requestPath.startsWith("/user/") ||
                requestPath.startsWith("/queue/")) {
            return true;
        }

        return false;
    }

    private Collection<GrantedAuthority> getAuthoritiesFromJwt(Claims claims) {
        List<String> roles = claims.get("roles", List.class);
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }

        return roles.stream()
                .map(role -> new SimpleGrantedAuthority(role))
                .collect(Collectors.toList());
    }
}