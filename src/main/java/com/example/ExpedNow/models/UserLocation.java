package com.example.ExpedNow.models;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document(collection = "user_locations")
public class UserLocation {
    @Id
    private String id;

    private String userId;

    private double latitude;

    private double longitude;

    private Date lastUpdated;
}
