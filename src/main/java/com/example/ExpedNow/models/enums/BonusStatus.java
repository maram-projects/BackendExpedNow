package com.example.ExpedNow.models.enums;

public enum BonusStatus {
    PENDING,    // Bonus calculated but not yet approved
    APPROVED,   // Bonus approved by admin but not yet paid
    PAID,       // Bonus paid to delivery person
    REJECTED    // Bonus rejected by admin
}
