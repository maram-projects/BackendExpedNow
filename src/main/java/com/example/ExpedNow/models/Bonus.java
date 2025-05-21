package com.example.ExpedNow.models;

import com.example.ExpedNow.models.enums.BonusStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "bonuses")
public class Bonus {

    @Id
    private String id;
    private String deliveryPersonId;
    private double amount;
    private int deliveryCount;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private BonusStatus status;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt = LocalDateTime.now();
    private String rejectionReason;

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }
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
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}