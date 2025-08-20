package com.example.ExpedNow.controllers;

import com.example.ExpedNow.dto.AIAssistantRequest;
import com.example.ExpedNow.dto.AIAssistantResponse;
import com.example.ExpedNow.services.core.impl.AIAssistantService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/ai-assistant")
@CrossOrigin(origins = "*")
public class AIAssistantController {

    private static final Logger logger = LoggerFactory.getLogger(AIAssistantController.class);

    @Autowired
    private AIAssistantService aiAssistantService;

    @PostMapping("/chat")
    @PreAuthorize("hasRole('CLIENT') or hasRole('DELIVERY_PERSON') or hasRole('ADMIN')")
    public ResponseEntity<AIAssistantResponse> chat(@Valid @RequestBody AIAssistantRequest request) {
        try {
            logger.info("Received AI assistant request from user: {}", request.getUserId());

            AIAssistantResponse response = aiAssistantService.processUserMessage(
                    request.getMessage(),
                    request.getUserId(),
                    request.getConversationId()
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error processing AI assistant request: {}", e.getMessage(), e);

            AIAssistantResponse errorResponse = new AIAssistantResponse();
            errorResponse.setSuccess(false);
            errorResponse.setMessage("Sorry, I'm having trouble processing your request right now. Please try again later.");
            errorResponse.setError(e.getMessage());

            return ResponseEntity.ok(errorResponse);
        }
    }

    @GetMapping("/conversation/{conversationId}")
    @PreAuthorize("hasRole('CLIENT') or hasRole('DELIVERY_PERSON') or hasRole('ADMIN')")
    public ResponseEntity<AIAssistantResponse> getConversationHistory(
            @PathVariable String conversationId,
            @RequestParam String userId) {
        try {
            logger.info("Fetching conversation history: {} for user: {}", conversationId, userId);

            AIAssistantResponse response = aiAssistantService.getConversationHistory(conversationId, userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error fetching conversation history: {}", e.getMessage(), e);

            AIAssistantResponse errorResponse = new AIAssistantResponse();
            errorResponse.setSuccess(false);
            errorResponse.setError("Failed to fetch conversation history");

            return ResponseEntity.ok(errorResponse);
        }
    }

    @PostMapping("/clear-conversation")
    @PreAuthorize("hasRole('CLIENT') or hasRole('DELIVERY_PERSON') or hasRole('ADMIN')")
    public ResponseEntity<AIAssistantResponse> clearConversation(@RequestBody AIAssistantRequest request) {
        try {
            logger.info("Clearing conversation: {} for user: {}", request.getConversationId(), request.getUserId());

            aiAssistantService.clearConversation(request.getConversationId(), request.getUserId());

            AIAssistantResponse response = new AIAssistantResponse();
            response.setSuccess(true);
            response.setMessage("Conversation cleared successfully");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error clearing conversation: {}", e.getMessage(), e);

            AIAssistantResponse errorResponse = new AIAssistantResponse();
            errorResponse.setSuccess(false);
            errorResponse.setError("Failed to clear conversation");

            return ResponseEntity.ok(errorResponse);
        }
    }
}