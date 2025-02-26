package com.example.ExpedNow.dto;

import lombok.Data;
import com.example.ExpedNow.models.Role;
import java.util.Date;
import java.util.Set;

@Data
public class UserDTO {
    private String id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String address;
    private Date dateOfRegistration;
    private Set<String> roles;
}