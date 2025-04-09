package com.example.ExpedNow.models;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document(collection = "notifications")
public class Notification {
    @Id
    private String id;

    private String userId;

    private String type;

    private String title;

    private String message;

    private String referenceId;

    private Date createdAt;

    private boolean read;
}
