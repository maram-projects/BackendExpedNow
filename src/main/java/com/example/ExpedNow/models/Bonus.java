package com.example.ExpedNow.models;

import com.example.ExpedNow.models.enums.BonusStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "bonuses")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Bonus {
    @Id
    private String id;
    private String deliveryPersonId;
    private double amount;
    private int deliveryCount;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private BonusStatus status;

    // Enhanced fields for better bonus management
    private String description;
    private String criteria;
    private String type;
    private String reason; // Why this bonus was created
    private String deliveryId; // Optional: specific delivery that earned this bonus
    private String bonusType; // WEEKLY_AUTO, MANUAL, PERFORMANCE, etc.
    private String notes;
    private String deliveryPersonName; // Cached for display
    private String deliveryPersonEmail; // Cached for display
    private boolean isActive = true;

    // Audit fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime paidAt;
    private LocalDateTime rejectedAt;
    private String paidBy; // Admin who paid the bonus
    private String rejectedBy; // Admin who rejected the bonus
    private String rejectionReason;
    private String createdBy; // Admin who created the bonus

    // Constructors
    public Bonus() {
        this.createdAt = LocalDateTime.now();
        this.status = BonusStatus.CREATED; // Default status when created
        this.isActive = true;
    }

    public Bonus(String deliveryPersonId, double amount, int deliveryCount) {
        this();
        this.deliveryPersonId = deliveryPersonId;
        this.amount = amount;
        this.deliveryCount = deliveryCount;
    }

    public Bonus(String deliveryPersonId, double amount, String reason, String createdBy) {
        this();
        this.deliveryPersonId = deliveryPersonId;
        this.amount = amount;
        this.reason = reason;
        this.createdBy = createdBy;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDeliveryPersonId() {
        return deliveryPersonId;
    }

    public void setDeliveryPersonId(String deliveryPersonId) {
        this.deliveryPersonId = deliveryPersonId;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public int getDeliveryCount() {
        return deliveryCount;
    }

    public void setDeliveryCount(int deliveryCount) {
        this.deliveryCount = deliveryCount;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }

    public BonusStatus getStatus() {
        return status;
    }

    public void setStatus(BonusStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCriteria() {
        return criteria;
    }

    public void setCriteria(String criteria) {
        this.criteria = criteria;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getDeliveryId() {
        return deliveryId;
    }

    public void setDeliveryId(String deliveryId) {
        this.deliveryId = deliveryId;
    }

    public String getBonusType() {
        return bonusType;
    }

    public void setBonusType(String bonusType) {
        this.bonusType = bonusType;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getDeliveryPersonName() {
        return deliveryPersonName;
    }

    public void setDeliveryPersonName(String deliveryPersonName) {
        this.deliveryPersonName = deliveryPersonName;
    }

    public String getDeliveryPersonEmail() {
        return deliveryPersonEmail;
    }

    public void setDeliveryPersonEmail(String deliveryPersonEmail) {
        this.deliveryPersonEmail = deliveryPersonEmail;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public LocalDateTime getRejectedAt() {
        return rejectedAt;
    }

    public void setRejectedAt(LocalDateTime rejectedAt) {
        this.rejectedAt = rejectedAt;
    }

    public String getPaidBy() {
        return paidBy;
    }

    public void setPaidBy(String paidBy) {
        this.paidBy = paidBy;
    }

    public String getRejectedBy() {
        return rejectedBy;
    }

    public void setRejectedBy(String rejectedBy) {
        this.rejectedBy = rejectedBy;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    // Utility methods
    public boolean canBePaid() {
        return this.status == BonusStatus.CREATED;
    }

    public boolean canBeRejected() {
        return this.status == BonusStatus.CREATED;
    }

    public boolean isPaid() {
        return this.status == BonusStatus.PAID;
    }

    public boolean isRejected() {
        return this.status == BonusStatus.REJECTED;
    }

    public void markAsPaid(String paidByAdmin) {
        this.status = BonusStatus.PAID;
        this.paidAt = LocalDateTime.now();
        this.paidBy = paidByAdmin;
        this.updatedAt = LocalDateTime.now();
    }

    public void markAsRejected(String rejectedByAdmin, String reason) {
        this.status = BonusStatus.REJECTED;
        this.rejectedAt = LocalDateTime.now();
        this.rejectedBy = rejectedByAdmin;
        this.rejectionReason = reason;
        this.updatedAt = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "Bonus{" +
                "id='" + id + '\'' +
                ", deliveryPersonId='" + deliveryPersonId + '\'' +
                ", amount=" + amount +
                ", status=" + status +
                ", reason='" + reason + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}