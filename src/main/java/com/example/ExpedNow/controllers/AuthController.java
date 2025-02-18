package com.example.ExpedNow.controllers;

import com.example.ExpedNow.models.Role;
import com.example.ExpedNow.models.User;
import com.example.ExpedNow.repositories.UserRepository;
import com.example.ExpedNow.security.JwtUtil;
import com.example.ExpedNow.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private UserService userService;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping("/register")
    public User register(@RequestBody User user, @RequestParam String userType) {
        Set<Role> roles = new HashSet<>();

        switch (userType.toLowerCase()) {
            case "admin":
                roles.add(Role.ROLE_ADMIN);
                break;
            case "client":
                roles.add(Role.ROLE_CLIENT);
                break;
            case "individual":
                roles.add(Role.ROLE_CLIENT);
                roles.add(Role.ROLE_INDIVIDUAL);
                break;
            case "enterprise":
                roles.add(Role.ROLE_CLIENT);
                roles.add(Role.ROLE_ENTERPRISE);
                break;
            case "delivery_person":
                roles.add(Role.ROLE_DELIVERY_PERSON);
                break;
            case "professional":
                roles.add(Role.ROLE_DELIVERY_PERSON);
                roles.add(Role.ROLE_PROFESSIONAL);
                break;
            case "temporary":
                roles.add(Role.ROLE_DELIVERY_PERSON);
                roles.add(Role.ROLE_TEMPORARY);
                break;
            default:
                throw new RuntimeException("نوع المستخدم غير صالح!");
        }

        user.setRoles(roles);
        return userService.registerUser(user);
    }


    @PostMapping("/login")
    public Map<String, String> login(@RequestBody User user) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(user.getEmail(), user.getPassword())
        );
        String token = jwtUtil.generateToken(user.getEmail());
        Map<String, String> response = new HashMap<>();
        response.put("token", token);
        return response;
    }
}
