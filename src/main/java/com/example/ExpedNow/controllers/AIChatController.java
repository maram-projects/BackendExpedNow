package com.example.ExpedNow.controllers;

import com.example.ExpedNow.dto.chatbot.OpenAIRequest;
import com.example.ExpedNow.dto.chatbot.OpenAIResponse;
import com.example.ExpedNow.dto.chatbot.Message;
import com.example.ExpedNow.services.core.impl.IntelligentChatBotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = {"http://localhost:4200"}, allowCredentials = "true")
public class AIChatController {

    private static final Logger logger = LoggerFactory.getLogger(AIChatController.class);

    @Autowired
    private IntelligentChatBotService intelligentChatBotService;

    @Value("${openai.api.key}")
    private String openaiApiKey;

    @Value("${chatbot.fallback.enabled:true}")
    private boolean fallbackEnabled;

    @Value("${chatbot.fallback.priority:high}") // high = prefer intelligent fallback, low = prefer OpenAI
    private String fallbackPriority;

    @Value("${chatbot.rate-limiting.max-requests-per-hour:50}")
    private int maxRequestsPerHour;

    private final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";

    // Enhanced rate limiting and quota tracking
    private LocalDateTime quotaResetTime = LocalDateTime.now().plusHours(1);
    private boolean quotaExceeded = false;
    private int requestCount = 0;
    private String lastQuotaError = "";

    @PostMapping("/chat")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CLIENT', 'INDIVIDUAL', 'ENTERPRISE', 'DELIVERY_PERSON', 'PROFESSIONAL', 'TEMPORARY')")
    public ResponseEntity<Map<String, String>> chat(@RequestBody Map<String, String> request) {
        try {
            logger.info("Received authenticated chat request");

            String userMessage = request.get("message");
            if (userMessage == null || userMessage.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "reply", "الرسالة فارغة. يرجى كتابة سؤالك.",
                        "source", "validation"
                ));
            }

            // Get user context from authentication
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String userRole = getUserRole(authentication);
            String userId = getUserId(authentication);

            logger.info("User role: {} - User ID: {} - Processing message: {}", userRole, userId, userMessage);

            String aiResponse;
            String responseSource;
            Map<String, String> metadata = new HashMap<>();

            // Determine response strategy
            ResponseStrategy strategy = determineResponseStrategy();
            logger.info("Using response strategy: {}", strategy);

            switch (strategy) {
                case INTELLIGENT_FALLBACK:
                    aiResponse = intelligentChatBotService.generateIntelligentResponse(userMessage, userRole, userId);
                    responseSource = "intelligent_fallback";
                    metadata.put("strategy", "intelligent_with_real_data");
                    break;

                case OPENAI_PRIMARY:
                    try {
                        String contextualPrompt = buildContextualPrompt(userMessage, userRole);
                        OpenAIRequest openAIRequest = createContextualRequest(contextualPrompt, userRole);
                        aiResponse = sendToOpenAI(openAIRequest);
                        responseSource = "openai";
                        metadata.put("strategy", "openai_primary");
                    } catch (Exception e) {
                        logger.warn("OpenAI failed, falling back to intelligent response for message: {}", userMessage, e);
                        aiResponse = intelligentChatBotService.generateIntelligentResponse(userMessage, userRole, userId);
                        responseSource = "intelligent_fallback";
                        metadata.put("strategy", "openai_failed_fallback");
                        markQuotaIssue(e);
                    }
                    break;

                case BASIC_FALLBACK:
                default:
                    aiResponse = generateBasicFallbackResponse(userMessage, userRole);
                    responseSource = "basic_fallback";
                    metadata.put("strategy", "basic_fallback");
                    break;
            }

            Map<String, String> result = new HashMap<>();
            result.put("reply", aiResponse);
            result.put("userRole", userRole);
            result.put("source", responseSource);
            result.putAll(metadata);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("Error in authenticated chat endpoint", e);
            return ResponseEntity.status(500).body(Map.of(
                    "reply", "عذراً، حدث خطأ في المعالجة. يرجى المحاولة مرة أخرى.",
                    "source", "error",
                    "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/chat/public")
    public ResponseEntity<Map<String, String>> publicChat(@RequestBody Map<String, String> request) {
        try {
            logger.info("Received public chat request");

            String userMessage = request.get("message");
            if (userMessage == null || userMessage.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "reply", "الرسالة فارغة. يرجى كتابة سؤالك.",
                        "source", "validation"
                ));
            }

            logger.info("Processing public message: {}", userMessage);
            // Use intelligent service for public users too, but with limited access
            String aiResponse = intelligentChatBotService.generateIntelligentResponse(userMessage, "GUEST", "anonymous");

            Map<String, String> result = new HashMap<>();
            result.put("reply", aiResponse);
            result.put("source", "intelligent_fallback");
            result.put("strategy", "public_intelligent");

            logger.info("Successfully processed public chat request");
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("Error in public chat endpoint", e);
            return ResponseEntity.status(500).body(Map.of(
                    "reply", "عذراً، حدث خطأ في المعالجة. يرجى المحاولة مرة أخرى.",
                    "source", "error"
            ));
        }
    }

    private enum ResponseStrategy {
        INTELLIGENT_FALLBACK,  // Use our smart local service with real data
        OPENAI_PRIMARY,       // Try OpenAI first, fallback to intelligent
        BASIC_FALLBACK        // Use basic responses only
    }

    private ResponseStrategy determineResponseStrategy() {
        // Reset counters if needed
        resetCountersIfNeeded();

        // If fallback priority is set to high, always use intelligent fallback
        if ("high".equalsIgnoreCase(fallbackPriority)) {
            return ResponseStrategy.INTELLIGENT_FALLBACK;
        }

        // Check if quota is exceeded
        if (quotaExceeded && LocalDateTime.now().isBefore(quotaResetTime)) {
            return ResponseStrategy.INTELLIGENT_FALLBACK;
        }

        // Check hourly request count limit
        if (requestCount >= maxRequestsPerHour) {
            return ResponseStrategy.INTELLIGENT_FALLBACK;
        }

        // Check if API key is missing or invalid
        if (openaiApiKey == null || openaiApiKey.trim().isEmpty() ||
                "your-openai-api-key-here".equals(openaiApiKey) ||
                openaiApiKey.startsWith("sk-proj-") && openaiApiKey.length() < 50) {
            return ResponseStrategy.INTELLIGENT_FALLBACK;
        }

        // If everything is fine and priority is low, use OpenAI
        if ("low".equalsIgnoreCase(fallbackPriority)) {
            return ResponseStrategy.OPENAI_PRIMARY;
        }

        // Default to intelligent fallback for better user experience
        return ResponseStrategy.INTELLIGENT_FALLBACK;
    }

    private void resetCountersIfNeeded() {
        LocalDateTime now = LocalDateTime.now();

        // Reset hourly quota tracking if time has passed
        if (now.isAfter(quotaResetTime)) {
            quotaExceeded = false;
            requestCount = 0;
            quotaResetTime = now.plusHours(1);
            lastQuotaError = "";
            logger.info("Reset hourly counters - quota exceeded: {}, request count: {}", quotaExceeded, requestCount);
        }
    }

    private void markQuotaIssue(Exception e) {
        if (e instanceof HttpClientErrorException) {
            HttpClientErrorException httpError = (HttpClientErrorException) e;
            String responseBody = httpError.getResponseBodyAsString();

            if (httpError.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                quotaExceeded = true;
                quotaResetTime = LocalDateTime.now().plusHours(1);
                lastQuotaError = "Rate limit exceeded";
                logger.warn("Rate limit exceeded, switching to intelligent fallback until: {}", quotaResetTime);
            } else if (httpError.getStatusCode() == HttpStatus.PAYMENT_REQUIRED ||
                    responseBody.contains("insufficient_quota") ||
                    responseBody.contains("exceeded your current quota")) {
                quotaExceeded = true;
                quotaResetTime = LocalDateTime.now().plusDays(1); // Longer reset for quota issues
                lastQuotaError = "API quota exceeded";
                logger.error("OpenAI API quota exceeded, switching to intelligent fallback until: {}", quotaResetTime);
            } else if (httpError.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                quotaExceeded = true;
                quotaResetTime = LocalDateTime.now().plusHours(6); // Medium reset for auth issues
                lastQuotaError = "Authentication failed";
                logger.error("OpenAI API authentication failed, switching to intelligent fallback");
            }
        }
    }

    private String generateBasicFallbackResponse(String userMessage, String userRole) {
        // This is now a backup for when even the intelligent service fails
        boolean isArabic = userMessage.matches(".*[\u0600-\u06FF].*");
        boolean isFrench = userMessage.toLowerCase().contains("bonjour") ||
                userMessage.toLowerCase().contains("combien") ||
                userMessage.toLowerCase().contains("utilisateurs");

        if ("ADMIN".equals(userRole)) {
            if (isFrench) {
                return "Bonjour ! Je suis l'assistant administrateur en mode limité. Je peux vous aider avec les questions de base sur la gestion du système.";
            } else {
                return "مرحباً! أنا المساعد الإداري في الوضع المحدود. يمكنني مساعدتك في الأسئلة الأساسية حول إدارة النظام.";
            }
        } else if (userRole.contains("CLIENT") || userRole.contains("INDIVIDUAL") || userRole.contains("ENTERPRISE")) {
            if (isFrench) {
                return "Bonjour ! Je peux vous aider avec vos commandes et questions sur nos services de livraison.";
            } else {
                return "مرحباً! يمكنني مساعدتك في طلباتك والأسئلة حول خدمات التوصيل.";
            }
        } else if (userRole.contains("DELIVERY")) {
            if (isFrench) {
                return "Bonjour ! Je peux vous aider avec la gestion de vos livraisons et vos gains.";
            } else {
                return "مرحباً! يمكنني مساعدتك في إدارة توصيلاتك وأرباحك.";
            }
        } else {
            if (isFrench) {
                return "Bonjour ! Bienvenue chez ExpedNow. Je peux vous parler de nos services de livraison.";
            } else {
                return "مرحباً! أهلاً بك في ExpedNow. يمكنني إخبارك عن خدمات التوصيل لدينا.";
            }
        }
    }

    private String sendToOpenAI(OpenAIRequest openAIRequest) throws Exception {
        // Increment request count
        requestCount++;

        try {
            // Validate API key
            if (openaiApiKey == null || openaiApiKey.trim().isEmpty()) {
                logger.error("OpenAI API key is not configured");
                throw new Exception("OpenAI API key is not configured");
            }

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + openaiApiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<OpenAIRequest> entity = new HttpEntity<>(openAIRequest, headers);

            logger.info("Sending request to OpenAI API (Request #{} this hour)", requestCount);
            ResponseEntity<OpenAIResponse> response = restTemplate.postForEntity(
                    OPENAI_URL, entity, OpenAIResponse.class);

            if (response.getBody() != null && response.getBody().getAnswer() != null) {
                logger.info("Received successful response from OpenAI");
                return response.getBody().getAnswer();
            } else {
                logger.warn("Empty response body from OpenAI");
                throw new Exception("Empty response from OpenAI");
            }

        } catch (HttpClientErrorException e) {
            logger.error("OpenAI API client error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());

            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new Exception("خطأ في المصادقة مع OpenAI API");
            } else if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                throw new Exception("تم تجاوز الحد المسموح من الطلبات. يرجى المحاولة لاحقاً");
            } else if (e.getStatusCode() == HttpStatus.PAYMENT_REQUIRED ||
                    e.getResponseBodyAsString().contains("insufficient_quota")) {
                throw new Exception("تم استنفاد رصيد OpenAI API");
            } else {
                throw new Exception("خطأ في طلب OpenAI API");
            }
        } catch (HttpServerErrorException e) {
            logger.error("OpenAI API server error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new Exception("خطأ في خادم OpenAI. يرجى المحاولة لاحقاً");
        } catch (ResourceAccessException e) {
            logger.error("Network error when calling OpenAI API", e);
            throw new Exception("خطأ في الشبكة. يرجى التحقق من الاتصال والمحاولة مرة أخرى");
        } catch (Exception e) {
            logger.error("Unexpected error when calling OpenAI API", e);
            throw new Exception("حدث خطأ غير متوقع");
        }
    }

    private String getUserRole(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return "GUEST";
        }

        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(auth -> !auth.startsWith("ROLE_"))
                .findFirst()
                .orElse("USER");
    }

    private String getUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return "anonymous";
        }
        return authentication.getName(); // This should be the user ID from JWT
    }

    private String buildContextualPrompt(String userMessage, String userRole) {
        StringBuilder contextBuilder = new StringBuilder();

        // Add system context based on user role
        contextBuilder.append("You are an AI assistant for ExpedNow, a delivery management system. ");

        switch (userRole) {
            case "ADMIN":
                contextBuilder.append("You are helping an administrator. You can provide information about system management, user oversight, financial reports, and administrative functions. ");
                break;
            case "CLIENT":
            case "INDIVIDUAL":
            case "ENTERPRISE":
                contextBuilder.append("You are helping a client. You can provide information about placing orders, tracking deliveries, payment methods, and client services. ");
                break;
            case "DELIVERY_PERSON":
            case "PROFESSIONAL":
            case "TEMPORARY":
                contextBuilder.append("You are helping a delivery person. You can provide information about delivery assignments, route optimization, earnings, and delivery procedures. ");
                break;
            default:
                contextBuilder.append("You are providing general information about ExpedNow services. ");
                break;
        }

        contextBuilder.append("Respond in the same language as the user's question. ");
        contextBuilder.append("Be helpful, professional, and concise. ");
        contextBuilder.append("If you don't know specific information about the system, provide general helpful advice related to delivery services. ");
        contextBuilder.append("\n\nUser question: ").append(userMessage);

        return contextBuilder.toString();
    }

    private OpenAIRequest createContextualRequest(String contextualPrompt, String userRole) {
        List<Message> messages = new ArrayList<>();

        // System message to set context
        messages.add(new Message("system",
                "You are a smart assistant for ExpedNow delivery management system. " +
                        "Answer in the same language as the user's question (Arabic, French, or English). " +
                        "Be helpful, professional, and concise in your responses. " +
                        "If you don't know specific information, provide general helpful advice related to delivery services."
        ));

        // User message with context
        messages.add(new Message("user", contextualPrompt));

        OpenAIRequest request = new OpenAIRequest();
        request.setModel("gpt-3.5-turbo");
        request.setMessages(messages);
        request.setMaxTokens(300);
        request.setTemperature(0.7);

        return request;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        try {
            ResponseStrategy currentStrategy = determineResponseStrategy();

            health.put("status", "active");
            health.put("currentStrategy", currentStrategy.toString());
            health.put("quotaExceeded", quotaExceeded);
            health.put("requestsThisHour", requestCount);
            health.put("maxRequestsPerHour", maxRequestsPerHour);
            health.put("fallbackEnabled", fallbackEnabled);
            health.put("fallbackPriority", fallbackPriority);
            health.put("quotaResetTime", quotaResetTime != null ? quotaResetTime.toString() : "N/A");
            health.put("lastQuotaError", lastQuotaError);

            // API key status (without exposing the actual key)
            health.put("apiKeyConfigured", openaiApiKey != null && !openaiApiKey.trim().isEmpty() &&
                    !"your-openai-api-key-here".equals(openaiApiKey));

            // Service status
            health.put("intelligentServiceEnabled", intelligentChatBotService != null);

        } catch (Exception e) {
            logger.error("Health check error", e);
            health.put("status", "error");
            health.put("error", e.getMessage());
        }
        return ResponseEntity.ok(health);
    }

    @GetMapping("/quota-status")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Map<String, Object>> getQuotaStatus() {
        Map<String, Object> status = new HashMap<>();
        ResponseStrategy currentStrategy = determineResponseStrategy();

        status.put("currentStrategy", currentStrategy.toString());
        status.put("quotaExceeded", quotaExceeded);
        status.put("requestsThisHour", requestCount);
        status.put("maxRequestsPerHour", maxRequestsPerHour);
        status.put("quotaResetTime", quotaResetTime.toString());
        status.put("lastQuotaError", lastQuotaError);
        status.put("fallbackPriority", fallbackPriority);
        status.put("recommendedAction", getRecommendedAction(currentStrategy));

        return ResponseEntity.ok(status);
    }

    private String getRecommendedAction(ResponseStrategy strategy) {
        switch (strategy) {
            case INTELLIGENT_FALLBACK:
                if (quotaExceeded) {
                    return "OpenAI quota exceeded. System is using intelligent fallback with real data - performance should be good.";
                } else {
                    return "System is using intelligent fallback by configuration. This provides fast, accurate responses with real data.";
                }
            case OPENAI_PRIMARY:
                return "System is using OpenAI API. Monitor usage to avoid quota issues.";
            case BASIC_FALLBACK:
                return "System is using basic fallback. Consider fixing configuration or enabling intelligent service.";
            default:
                return "Unknown strategy. Check system configuration.";
        }
    }
}