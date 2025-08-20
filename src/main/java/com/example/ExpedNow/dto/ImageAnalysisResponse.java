package com.example.ExpedNow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ImageAnalysisResponse {
    private boolean success;
    private String timestamp;
    private Analysis analysis;
    private DeliveryRelevantInfo deliveryRelevantInfo;
    private String error;

    @JsonProperty("image_analyzed")
    private boolean imageAnalyzed;

    @JsonProperty("image_quality")
    private String imageQuality;

    @JsonProperty("extracted_text")
    private String extractedText;



    public ImageAnalysisResponse withAnalysisData(Analysis.TextExtraction textExtraction) {
        this.imageAnalyzed = true;
        this.imageQuality = deliveryRelevantInfo.getImageQuality();
        this.extractedText = textExtraction != null ? textExtraction.getFullText() : "";
        return this;
    }
    @Data
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Analysis {
        private TextExtraction textExtraction;
        private ImageProperties imageProperties;

        @Data
        @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
        public static class TextExtraction {
            private String fullText;
            private List<Word> words;
            private int wordCount;

            @Data
            public static class Word {
                private String text;
                private int confidence;
            }
        }

        @Data
        @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
        public static class ImageProperties {
            private int width;
            private int height;
            private int channels;
            private double brightness;
            private int contoursCount;
            private String error;
        }
    }

    @Data
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class DeliveryRelevantInfo {
        private boolean hasText;
        private String imageQuality;
        private boolean suitableForDelivery;
        private String analyzedAt;  // Add this field
        // Add setter for analyzedAt
        public void setAnalyzedAt(String analyzedAt) {
            this.analyzedAt = analyzedAt;
        }
    }
}