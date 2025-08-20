package com.example.ExpedNow.dto.chatbot;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class OpenAIRequest {
    private String model = "gpt-3.5-turbo";
    private List<Message> messages;

    @JsonProperty("max_tokens")
    private int maxTokens = 1000;

    private double temperature = 0.7;

    // Default constructor
    public OpenAIRequest() {}

    // Constructor for simple prompt (backward compatibility)
    public OpenAIRequest(String prompt) {
        this.messages = List.of(new Message("user", prompt));
    }

    // Constructor for message list
    public OpenAIRequest(List<Message> messages) {
        this.messages = messages;
    }

    // Getters and setters
    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    @Override
    public String toString() {
        return "OpenAIRequest{" +
                "model='" + model + '\'' +
                ", messages=" + messages +
                ", maxTokens=" + maxTokens +
                ", temperature=" + temperature +
                '}';
    }
}