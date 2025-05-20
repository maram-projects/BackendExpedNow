package com.example.ExpedNow.models;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@Document(collection = "password_reset_tokens") // اسم المجموعة في MongoDB
public class PasswordResetToken {
    @Id
    private String id;
    private String token;          // الرمز السري
    private String userId;         // ID المستخدم المرتبط
    private LocalDateTime expiryDate; // تاريخ انتهاء الصلاحية
}