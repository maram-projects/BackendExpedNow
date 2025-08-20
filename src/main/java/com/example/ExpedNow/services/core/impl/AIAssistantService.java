package com.example.ExpedNow.services.core.impl;

import com.example.ExpedNow.dto.AIAssistantResponse;
import com.example.ExpedNow.models.AIConversation;
import com.example.ExpedNow.models.AIMessage;
import com.example.ExpedNow.models.DeliveryRequest;
import com.example.ExpedNow.models.User;
import com.example.ExpedNow.repositories.AIConversationRepository;
import com.example.ExpedNow.repositories.DeliveryReqRepository;
import com.example.ExpedNow.repositories.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AIAssistantService {

    private static final Logger logger = LoggerFactory.getLogger(AIAssistantService.class);

    @Value("${openai.api.key}")
    private String openaiApiKey;

    @Value("${openai.api.url}")
    private String openaiApiUrl;

    @Value("${openai.api.model:gpt-3.5-turbo}")
    private String model;

    @Value("${openai.api.max-tokens:500}")
    private int maxTokens;

    @Value("${openai.api.temperature:0.7}")
    private double temperature;

    @Autowired
    private AIConversationRepository conversationRepository;

    @Autowired
    private DeliveryReqRepository deliveryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    public AIAssistantResponse processUserMessage(String message, String userId, String conversationId) {
        try {
            // Get or create conversation
            AIConversation conversation = getOrCreateConversation(conversationId, userId);

            // Add user message to conversation
            AIMessage userMessage = new AIMessage();
            userMessage.setContent(message);
            userMessage.setRole("user");
            userMessage.setTimestamp(LocalDateTime.now());
            conversation.addMessage(userMessage);

            // Get user context for better responses
            String contextualMessage = buildContextualMessage(message, userId);

            // Get AI response
            String aiResponse = getOpenAIResponse(contextualMessage, conversation.getMessages());

            // Add AI response to conversation
            AIMessage assistantMessage = new AIMessage();
            assistantMessage.setContent(aiResponse);
            assistantMessage.setRole("assistant");
            assistantMessage.setTimestamp(LocalDateTime.now());
            conversation.addMessage(assistantMessage);

            // Save conversation
            conversationRepository.save(conversation);

            // Build response
            AIAssistantResponse response = new AIAssistantResponse();
            response.setSuccess(true);
            response.setMessage(aiResponse);
            response.setConversationId(conversation.getId());
            response.setTimestamp(LocalDateTime.now());

            return response;

        } catch (Exception e) {
            logger.error("Error processing AI assistant message: {}", e.getMessage(), e);

            AIAssistantResponse errorResponse = new AIAssistantResponse();
            errorResponse.setSuccess(false);
            errorResponse.setMessage("I'm sorry, I'm having trouble processing your request right now. Please try again.");
            errorResponse.setError(e.getMessage());

            return errorResponse;
        }
    }

    private AIConversation getOrCreateConversation(String conversationId, String userId) {
        if (conversationId != null) {
            Optional<AIConversation> existing = conversationRepository.findById(conversationId);
            if (existing.isPresent() && existing.get().getUserId().equals(userId)) {
                return existing.get();
            }
        }

        // Create new conversation
        AIConversation conversation = new AIConversation();
        conversation.setUserId(userId);
        conversation.setStartTime(LocalDateTime.now());
        conversation.setLastActivity(LocalDateTime.now());
        conversation.setMessages(new ArrayList<>());

        return conversationRepository.save(conversation);
    }

    private String buildContextualMessage(String message, String userId) {
        try {
            // Get user information
            Optional<User> userOpt = userRepository.findById(userId);
            if (!userOpt.isPresent()) {
                return message;
            }

            User user = userOpt.get();
            StringBuilder context = new StringBuilder();

            // Add user context
            context.append("User Information:\n");
            context.append("- Name: ").append(user.getFirstName()).append(" ").append(user.getLastName()).append("\n");
            context.append("- User Type: ").append(user.getUserType()).append("\n");
            context.append("- Email: ").append(user.getEmail()).append("\n");

            // Add delivery statistics for clients
            if ("CLIENT".equals(user.getUserType())) {
                List<DeliveryRequest> deliveries = deliveryRepository.findByClientId(userId);

                long totalDeliveries = deliveries.size();
                long completedDeliveries = deliveries.stream()
                        .mapToLong(d -> "DELIVERED".equals(d.getStatus()) ? 1 : 0)
                        .sum();
                long pendingDeliveries = deliveries.stream()
                        .mapToLong(d -> Arrays.asList("PENDING", "ASSIGNED", "IN_TRANSIT").contains(d.getStatus()) ? 1 : 0)
                        .sum();
                long cancelledDeliveries = deliveries.stream()
                        .mapToLong(d -> "CANCELLED".equals(d.getStatus()) ? 1 : 0)
                        .sum();

                // Calculate total spent
                double totalSpent = deliveries.stream()
                        .filter(d -> "DELIVERED".equals(d.getStatus()))
                        .mapToDouble(d -> d.getAmount() != null ? d.getAmount() : 0.0)
                        .sum();

                // Recent deliveries
                List<DeliveryRequest> recentDeliveries = deliveries.stream()
                        .filter(d -> d.getCreatedAt() != null)
                        .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                        .limit(3)
                        .collect(Collectors.toList());

                context.append("\nDelivery Statistics:\n");
                context.append("- Total Deliveries: ").append(totalDeliveries).append("\n");
                context.append("- Completed Deliveries: ").append(completedDeliveries).append("\n");
                context.append("- Pending Deliveries: ").append(pendingDeliveries).append("\n");
                context.append("- Cancelled Deliveries: ").append(cancelledDeliveries).append("\n");
                context.append("- Total Amount Spent: ").append(String.format("%.2f TND", totalSpent)).append("\n");

                if (!recentDeliveries.isEmpty()) {
                    context.append("\nRecent Deliveries:\n");
                    for (DeliveryRequest delivery : recentDeliveries) {
                        context.append("- ID: ").append(delivery.getId().substring(0, 8))
                                .append(", Status: ").append(delivery.getStatus())
                                .append(", To: ").append(delivery.getDeliveryAddress())
                                .append(", Amount: ").append(delivery.getAmount()).append(" TND\n");
                    }
                }

                // Unpaid deliveries
                long unpaidDeliveries = deliveries.stream()
                        .filter(d -> "DELIVERED".equals(d.getStatus()))
                        .mapToLong(d -> d.getPaymentStatus() != null &&
                                !"COMPLETED".equals(d.getPaymentStatus().toString()) ? 1 : 0)
                        .sum();

                if (unpaidDeliveries > 0) {
                    context.append("- Unpaid Deliveries: ").append(unpaidDeliveries).append("\n");
                }
            }

            context.append("\nUser Message: ").append(message);

            return context.toString();

        } catch (Exception e) {
            logger.warn("Error building contextual message: {}", e.getMessage());
            return message;
        }
    }

    private String getOpenAIResponse(String message, List<AIMessage> conversationHistory) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openaiApiKey);

        // Build messages for OpenAI
        List<Map<String, String>> messages = new ArrayList<>();

        // System message
        messages.add(Map.of(
                "role", "system",
                "content", buildSystemPrompt()
        ));

        // Add recent conversation history (last 5 messages to stay within token limit)
        List<AIMessage> recentHistory = conversationHistory.stream()
                .skip(Math.max(0, conversationHistory.size() - 5))
                .collect(Collectors.toList());

        for (AIMessage msg : recentHistory) {
            if (msg.getContent() != null && !msg.getContent().trim().isEmpty()) {
                messages.add(Map.of(
                        "role", msg.getRole(),
                        "content", msg.getContent()
                ));
            }
        }

        // Add current message
        messages.add(Map.of(
                "role", "user",
                "content", message
        ));

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", messages,
                "max_tokens", maxTokens,
                "temperature", temperature
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        logger.info("Sending request to OpenAI with {} messages", messages.size());

        ResponseEntity<String> response = restTemplate.exchange(
                openaiApiUrl,
                HttpMethod.POST,
                entity,
                String.class
        );

        if (response.getStatusCode() == HttpStatus.OK) {
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
            return jsonResponse.path("choices").get(0).path("message").path("content").asText();
        } else {
            throw new RuntimeException("OpenAI API request failed with status: " + response.getStatusCode());
        }
    }

    private String buildSystemPrompt() {
        return """
            You are an AI assistant for ExpedNow, a delivery service platform. You help users with:
            
            1. **For Clients:**
            - Information about their deliveries (status, tracking, history)
            - Help with creating new delivery requests
            - Payment and billing inquiries
            - Delivery pricing and estimates
            - Account management
            - Troubleshooting delivery issues
            
            2. **General Guidelines:**
            - Be helpful, friendly, and professional
            - Provide accurate information based on the user's data
            - If you don't have specific information, suggest they contact support
            - Keep responses concise but informative
            - Use the context provided about the user's deliveries and account
            - Always respond in the same language the user is using
            - For monetary amounts, use "TND" as the currency
            
            3. **Key Features to Highlight:**
            - Real-time delivery tracking
            - Multiple payment options
            - Professional delivery personnel
            - Secure package handling
            - Competitive pricing
            
            When users ask about their deliveries, orders, or account, use the provided context to give specific, personalized responses.
            """;
    }

    public AIAssistantResponse getConversationHistory(String conversationId, String userId) {
        try {
            Optional<AIConversation> conversationOpt = conversationRepository.findById(conversationId);

            if (!conversationOpt.isPresent() || !conversationOpt.get().getUserId().equals(userId)) {
                AIAssistantResponse response = new AIAssistantResponse();
                response.setSuccess(false);
                response.setError("Conversation not found");
                return response;
            }

            AIConversation conversation = conversationOpt.get();

            AIAssistantResponse response = new AIAssistantResponse();
            response.setSuccess(true);
            response.setConversationId(conversationId);
            response.setMessages(conversation.getMessages());
            response.setTimestamp(LocalDateTime.now());

            return response;

        } catch (Exception e) {
            logger.error("Error fetching conversation history: {}", e.getMessage(), e);

            AIAssistantResponse errorResponse = new AIAssistantResponse();
            errorResponse.setSuccess(false);
            errorResponse.setError("Failed to fetch conversation history");

            return errorResponse;
        }
    }

    public void clearConversation(String conversationId, String userId) {
        try {
            Optional<AIConversation> conversationOpt = conversationRepository.findById(conversationId);

            if (conversationOpt.isPresent() && conversationOpt.get().getUserId().equals(userId)) {
                conversationRepository.deleteById(conversationId);
                logger.info("Cleared conversation: {} for user: {}", conversationId, userId);
            }
        } catch (Exception e) {
            logger.error("Error clearing conversation: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to clear conversation", e);
        }
    }
}