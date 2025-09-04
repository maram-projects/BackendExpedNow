// BonusStatus.java
package com.example.ExpedNow.models.enums;

public enum BonusStatus {
    CREATED,    // Bonus created by admin, ready for payment
    PAID,       // Bonus has been paid to delivery person
    REJECTED    // Bonus has been rejected/cancelled
}