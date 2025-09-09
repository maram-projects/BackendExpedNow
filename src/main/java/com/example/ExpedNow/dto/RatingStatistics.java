package com.example.ExpedNow.dto;

public class RatingStatistics {
    private int totalRatings;
    private double averageRating;
    private int fiveStars;
    private int fourStars;
    private int threeStars;
    private int twoStars;
    private int oneStar;

    public RatingStatistics() {}

    public RatingStatistics(int totalRatings, double averageRating, int fiveStars,
                            int fourStars, int threeStars, int twoStars, int oneStar) {
        this.totalRatings = totalRatings;
        this.averageRating = averageRating;
        this.fiveStars = fiveStars;
        this.fourStars = fourStars;
        this.threeStars = threeStars;
        this.twoStars = twoStars;
        this.oneStar = oneStar;
    }

    // Getters
    public int getTotalRatings() {
        return totalRatings;
    }

    public double getAverageRating() {
        return averageRating;
    }

    public int getFiveStars() {
        return fiveStars;
    }

    public int getFourStars() {
        return fourStars;
    }

    public int getThreeStars() {
        return threeStars;
    }

    public int getTwoStars() {
        return twoStars;
    }

    public int getOneStar() {
        return oneStar;
    }

    // Setters
    public void setTotalRatings(int totalRatings) {
        this.totalRatings = totalRatings;
    }

    public void setAverageRating(double averageRating) {
        this.averageRating = averageRating;
    }

    public void setFiveStars(int fiveStars) {
        this.fiveStars = fiveStars;
    }

    public void setFourStars(int fourStars) {
        this.fourStars = fourStars;
    }

    public void setThreeStars(int threeStars) {
        this.threeStars = threeStars;
    }

    public void setTwoStars(int twoStars) {
        this.twoStars = twoStars;
    }

    public void setOneStar(int oneStar) {
        this.oneStar = oneStar;
    }
}