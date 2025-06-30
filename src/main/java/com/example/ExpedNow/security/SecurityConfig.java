package com.example.ExpedNow.security;

import com.example.ExpedNow.services.auth2.OAuth2UserService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    private final JwtFilter jwtFilter;
    private final UserDetailsService customUserDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final OAuth2UserService oAuth2UserService;

    public SecurityConfig(JwtFilter jwtFilter,
                          UserDetailsService customUserDetailsService,
                          PasswordEncoder passwordEncoder,
                          OAuth2UserService oAuth2UserService) {
        this.jwtFilter = jwtFilter;
        this.customUserDetailsService = customUserDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.oAuth2UserService = oAuth2UserService;
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(customUserDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(authenticationProvider());
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints - must come first
                        .requestMatchers(
                                "/uploads/**",
                                "/uploads/vehicle-photos/**",
                                "/api/auth/**",
                                "/oauth2/**",
                                "/ws/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/topic/**",
                                "/app/**",
                                "/user/**",
                                "/queue/**"
                        ).permitAll()

                        // Pricing endpoints
                        .requestMatchers("/api/pricing/**").permitAll()

                        // Chat endpoints - FIXED: Only one declaration and specific permissions
                        .requestMatchers("/api/chat/**").hasAnyAuthority("CLIENT", "INDIVIDUAL", "ENTERPRISE", "DELIVERY_PERSON", "PROFESSIONAL", "TEMPORARY", "ADMIN")

                        // Specific authenticated endpoints
                        .requestMatchers("/api/users/by-vehicle/**").authenticated()
                        .requestMatchers("/api/notifications/**").authenticated()

                        // Discounts endpoints
                        .requestMatchers("/api/discounts/**").hasAnyAuthority("CLIENT", "ENTERPRISE", "ADMIN")

                        // Payment endpoints - specific routes first (order matters!)
                        .requestMatchers(HttpMethod.GET, "/api/payments/client/**").hasAnyAuthority("CLIENT", "INDIVIDUAL", "ENTERPRISE", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/payments/delivery/**").hasAnyAuthority("CLIENT", "INDIVIDUAL", "ENTERPRISE", "DELIVERY_PERSON", "PROFESSIONAL", "TEMPORARY", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/payments/methods/*/supported").hasAnyAuthority("CLIENT", "INDIVIDUAL", "ENTERPRISE", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/payments/*/client-secret").hasAnyAuthority("CLIENT", "INDIVIDUAL", "ENTERPRISE", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/payments/*/status").hasAnyAuthority("CLIENT", "INDIVIDUAL", "ENTERPRISE", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/payments/*").hasAnyAuthority("CLIENT", "INDIVIDUAL", "ENTERPRISE", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/payments").hasAnyAuthority("CLIENT", "INDIVIDUAL", "ENTERPRISE", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/payments/confirm").hasAnyAuthority("CLIENT", "INDIVIDUAL", "ENTERPRISE", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/payments/fail").hasAnyAuthority("CLIENT", "INDIVIDUAL", "ENTERPRISE", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/payments/*/process").hasAnyAuthority("CLIENT", "INDIVIDUAL", "ENTERPRISE", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/payments/*/process/**").hasAnyAuthority("CLIENT", "INDIVIDUAL", "ENTERPRISE", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/payments/*/cancel").hasAnyAuthority("CLIENT", "INDIVIDUAL", "ENTERPRISE", "ADMIN")

                        // Admin-only payment endpoints
                        .requestMatchers(HttpMethod.GET, "/api/payments/all").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/payments/stats").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/payments").hasAuthority("ADMIN") // This is the paginated endpoint
                        .requestMatchers(HttpMethod.PUT, "/api/payments/*/status").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/payments/*/refund").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/payments/*/release-to-delivery").hasAuthority("ADMIN")

                        // Catch-all for any remaining payment endpoints - keep as admin only
                        .requestMatchers("/api/payments/**").hasAuthority("ADMIN")

                        // BONUS ENDPOINTS - FIXED ORDER
                        // Delivery person specific bonus endpoints - MUST come before catch-all
                        .requestMatchers(HttpMethod.GET, "/api/bonuses/delivery-person/**").hasAnyAuthority("ADMIN", "DELIVERY_PERSON", "PROFESSIONAL", "TEMPORARY")

                        // Admin-only bonus endpoints - catch-all MUST come last
                        .requestMatchers("/api/bonuses/**").hasAuthority("ADMIN")

                        // Availability endpoints
                        .requestMatchers("/api/availability/**").hasAnyAuthority("ADMIN", "DELIVERY_PERSON", "PROFESSIONAL", "TEMPORARY")

                        // Delivery endpoints
                        .requestMatchers("/api/deliveries/**").hasAnyAuthority("CLIENT", "INDIVIDUAL", "ENTERPRISE", "ADMIN", "DELIVERY_PERSON", "PROFESSIONAL", "TEMPORARY")

                        // Delivery person endpoints
                        .requestMatchers("/api/deliveriesperson/**").hasAnyAuthority("PROFESSIONAL", "TEMPORARY", "ADMIN", "DELIVERY_PERSON")

                        // Rating endpoints
                        .requestMatchers(HttpMethod.POST, "/api/deliveries/*/rate").hasAnyAuthority("CLIENT", "INDIVIDUAL", "ENTERPRISE")

                        // Admin endpoints
                        .requestMatchers("/api/admin/**").hasAuthority("ADMIN")

                        // Default to authenticated for any other requests
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"Access denied\"}");
                        })
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2Login(oauth2 -> oauth2
                        .defaultSuccessUrl("/oauth2/loginSuccess")
                        .failureUrl("/oauth2/loginFailure")
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(oAuth2UserService))
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(secretKey()).build();
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey()));
    }

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Bean
    public SecretKey secretKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList(
                "http://localhost:4200",
                "https://your-production-domain.com"
        ));

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "Accept",
                "Origin",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers",
                "token",
                "Sec-WebSocket-Protocol",
                "Sec-WebSocket-Version",
                "Sec-WebSocket-Key",
                "Connection",
                "Upgrade"
        ));

        configuration.setExposedHeaders(Arrays.asList(
                "Access-Control-Allow-Origin",
                "Access-Control-Allow-Credentials",
                "Authorization"
        ));

        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
        DefaultMethodSecurityExpressionHandler expressionHandler =
                new DefaultMethodSecurityExpressionHandler();
        expressionHandler.setDefaultRolePrefix("");
        return expressionHandler;
    }
}