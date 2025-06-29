package com.example.ExpedNow.config;

import com.example.ExpedNow.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);
    private final JwtUtil jwtUtil;

    public WebSocketAuthInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = extractToken(accessor);

            if (!StringUtils.hasText(token)) {
                logger.debug("No token provided in WebSocket connection attempt");
                // Instead of throwing exception, reject the connection
                accessor.setHeader("simpDisconnectCode", "403");
                return null;
            }

            try {
                if (!jwtUtil.validateToken(token)) {
                    logger.error("WebSocket authentication failed: Invalid token");
                    accessor.setHeader("simpDisconnectCode", "403");
                    return null;
                }

                String userId = jwtUtil.getUserIdFromToken(token);
                Authentication auth = new UsernamePasswordAuthenticationToken(
                        userId, null, jwtUtil.getAuthoritiesFromToken(token));

                accessor.setUser(auth);
                logger.debug("WebSocket authentication successful for user: {}", userId);

            } catch (Exception e) {
                logger.error("WebSocket authentication failed", e);
                accessor.setHeader("simpDisconnectCode", "403");
                return null;
            }
        }
        return message;
    }

    private String extractToken(StompHeaderAccessor accessor) {
        logger.debug("WebSocket connection headers: {}", accessor.toNativeHeaderMap());
        // Check Authorization header first
        List<String> authHeaders = accessor.getNativeHeader("Authorization");
        if (authHeaders != null && !authHeaders.isEmpty()) {
            String bearerToken = authHeaders.get(0);
            if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
                return bearerToken.substring(7);
            }
        }

        // Then check token header directly
        List<String> tokenHeaders = accessor.getNativeHeader("token");
        if (tokenHeaders != null && !tokenHeaders.isEmpty()) {
            return tokenHeaders.get(0);
        }

        // Finally check from handshake attributes
        return (String) accessor.getSessionAttributes().get("token");
    }
}