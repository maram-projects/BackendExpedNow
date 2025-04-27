package com.example.ExpedNow.dto;

public record StatusUpdateRequest(String status) {
    // يمكنك إضافة تحقق من الصحة إذا لزم الأمر
    public StatusUpdateRequest {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Status cannot be null or empty");
        }
    }
}