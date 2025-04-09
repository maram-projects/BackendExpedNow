package com.example.ExpedNow.config;

import com.example.ExpedNow.services.core.impl.UserServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.ArrayList;

@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

    @Autowired
    private UserServiceImpl userService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = null;

            // Try to get from header first (standard approach)
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
                logger.debug("Token extracted from Authorization header");
            } else if (StringUtils.hasText(authHeader)) {
                token = authHeader;
                logger.debug("Token extracted from Authorization header (no Bearer prefix)");
            }

            // If not in header, try from session attributes (SockJS handshake attributes)
            if (token == null) {
                Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
                if (sessionAttributes != null && sessionAttributes.containsKey("token")) {
                    token = (String) sessionAttributes.get("token");
                    logger.debug("Token extracted from session attributes");
                }
            }

            // As a last resort, check query parameters
            if (token == null && accessor.getNativeHeader("token") != null) {
                token = accessor.getFirstNativeHeader("token");
                logger.debug("Token extracted from token header");
            }

            if (token != null) {
                try {
                    // Extract user ID from token
                    String userId = userService.getUserIdFromToken("Bearer " + token);

                    if (userId != null) {
                        // Create authentication token with empty authorities
                        // This is simpler than trying to get authorities if that method isn't available
                        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                userId, null, new ArrayList<>());

                        // Set it in the accessor
                        accessor.setUser(auth);
                        logger.info("WebSocket authenticated for user: {}", userId);
                    } else {
                        logger.warn("Invalid token in WebSocket connection");
                    }
                } catch (Exception e) {
                    logger.error("Error authenticating WebSocket connection", e);
                }
            } else {
                logger.warn("No Authorization token found in WebSocket connection");
            }
        }
        return message;
    }
}