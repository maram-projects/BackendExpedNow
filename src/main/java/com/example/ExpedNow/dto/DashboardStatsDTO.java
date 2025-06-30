package com.example.ExpedNow.dto;

import java.util.List;
import java.util.Map;

public class DashboardStatsDTO {
    private int totalUsers;
    private int activeToday;
    private int deliveryPersons;
    private int pendingApprovals;
    private Map<String, Long> usersByRole;
    private List<UserDTO> recentRegistrations;

    // Payment related fields
    private Integer totalPayments;
    private Double totalRevenue;
    private Map<String, Integer> paymentStatusBreakdown;

    // Discount related fields
    private Integer totalDiscounts;
    private Integer activeDiscounts;
    private Map<String, Integer> discountTypeBreakdown;
    private Map<String, Integer> discountUsage;

    // Bonus related fields
    private Integer totalBonuses;
    private Map<String, Integer> bonusStatusBreakdown;
    private Double bonusAmountPaid;

    // Constructors
    public DashboardStatsDTO() {}

    // Getters and Setters
    public int getTotalUsers() {
        return totalUsers;
    }

    public void setTotalUsers(int totalUsers) {
        this.totalUsers = totalUsers;
    }

    public int getActiveToday() {
        return activeToday;
    }

    public void setActiveToday(int activeToday) {
        this.activeToday = activeToday;
    }

    public int getDeliveryPersons() {
        return deliveryPersons;
    }

    public void setDeliveryPersons(int deliveryPersons) {
        this.deliveryPersons = deliveryPersons;
    }

    public int getPendingApprovals() {
        return pendingApprovals;
    }

    public void setPendingApprovals(int pendingApprovals) {
        this.pendingApprovals = pendingApprovals;
    }

    public Map<String, Long> getUsersByRole() {
        return usersByRole;
    }

    public void setUsersByRole(Map<String, Long> usersByRole) {
        this.usersByRole = usersByRole;
    }

    public List<UserDTO> getRecentRegistrations() {
        return recentRegistrations;
    }

    public void setRecentRegistrations(List<UserDTO> recentRegistrations) {
        this.recentRegistrations = recentRegistrations;
    }

    public Integer getTotalPayments() {
        return totalPayments;
    }

    public void setTotalPayments(Integer totalPayments) {
        this.totalPayments = totalPayments;
    }

    public Double getTotalRevenue() {
        return totalRevenue;
    }

    public void setTotalRevenue(Double totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

    public Map<String, Integer> getPaymentStatusBreakdown() {
        return paymentStatusBreakdown;
    }

    public void setPaymentStatusBreakdown(Map<String, Integer> paymentStatusBreakdown) {
        this.paymentStatusBreakdown = paymentStatusBreakdown;
    }

    public Integer getTotalDiscounts() {
        return totalDiscounts;
    }

    public void setTotalDiscounts(Integer totalDiscounts) {
        this.totalDiscounts = totalDiscounts;
    }

    public Integer getActiveDiscounts() {
        return activeDiscounts;
    }

    public void setActiveDiscounts(Integer activeDiscounts) {
        this.activeDiscounts = activeDiscounts;
    }

    public Map<String, Integer> getDiscountTypeBreakdown() {
        return discountTypeBreakdown;
    }

    public void setDiscountTypeBreakdown(Map<String, Integer> discountTypeBreakdown) {
        this.discountTypeBreakdown = discountTypeBreakdown;
    }

    public Map<String, Integer> getDiscountUsage() {
        return discountUsage;
    }

    public void setDiscountUsage(Map<String, Integer> discountUsage) {
        this.discountUsage = discountUsage;
    }

    public Integer getTotalBonuses() {
        return totalBonuses;
    }

    public void setTotalBonuses(Integer totalBonuses) {
        this.totalBonuses = totalBonuses;
    }

    public Map<String, Integer> getBonusStatusBreakdown() {
        return bonusStatusBreakdown;
    }

    public void setBonusStatusBreakdown(Map<String, Integer> bonusStatusBreakdown) {
        this.bonusStatusBreakdown = bonusStatusBreakdown;
    }

    public Double getBonusAmountPaid() {
        return bonusAmountPaid;
    }

    public void setBonusAmountPaid(Double bonusAmountPaid) {
        this.bonusAmountPaid = bonusAmountPaid;
    }
}