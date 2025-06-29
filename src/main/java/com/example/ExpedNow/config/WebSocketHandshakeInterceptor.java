package com.example.ExpedNow.config;

import com.example.ExpedNow.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
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
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

@Component
public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketHandshakeInterceptor.class);
    private final JwtUtil jwtUtil;

    public WebSocketHandshakeInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {

        logger.info("🔗 WebSocket handshake attempt from: {}", request.getRemoteAddress());
        logger.info("📋 Request URI: {}", request.getURI());
        logger.info("🔍 Headers: {}", request.getHeaders());

        if (request instanceof ServletServerHttpRequest) {
            ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
            HttpServletRequest req = servletRequest.getServletRequest();

            // Log all query parameters
            logger.info("📝 Query parameters: {}", req.getParameterMap());

            // 1. Try query parameter
            String token = req.getParameter("token");
            logger.info("🎫 Token from query param: {}", token != null ? "Found" : "Not found");

            // 2. Try Authorization header
            if (token == null) {
                String authHeader = req.getHeader("Authorization");
                logger.info("🔐 Authorization header: {}", authHeader != null ? authHeader : "Not found");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    token = authHeader.substring(7);
                    logger.info("🎫 Token extracted from Authorization header");
                }
            }

            // 3. Try custom token header
            if (token == null) {
                token = req.getHeader("token");
                logger.info("🎫 Token from custom header: {}", token != null ? "Found" : "Not found");
            }

            // 4. Try X-Auth-Token header (common alternative)
            if (token == null) {
                token = req.getHeader("X-Auth-Token");
                logger.info("🎫 Token from X-Auth-Token header: {}", token != null ? "Found" : "Not found");
            }

            if (token != null) {
                attributes.put("token", token);
                logger.info("✅ Token found and stored in handshake attributes");
                return true;
            } else {
                logger.warn("❌ No token found in handshake - rejecting connection");
                logger.warn("💡 Make sure frontend sends token via:");
                logger.warn("   - Query parameter: ?token=your_jwt_token");
                logger.warn("   - Authorization header: Bearer your_jwt_token");
                logger.warn("   - Custom header: token: your_jwt_token");

                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
        }

        logger.warn("❌ Request is not ServletServerHttpRequest - rejecting");
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        if (exception != null) {
            logger.error("💥 WebSocket handshake failed: {}", exception.getMessage());
        } else {
            logger.info("🎉 WebSocket handshake completed successfully");
        }
    }

    private String extractTokenFromRequest(ServerHttpRequest request) {
        // Check Authorization header
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // Check token parameter in query string
        String query = request.getURI().getQuery();
        if (StringUtils.hasText(query)) {
            Map<String, String> queryParams = UriComponentsBuilder.fromUriString("?" + query)
                    .build()
                    .getQueryParams()
                    .toSingleValueMap();

            String token = queryParams.get("token");
            if (StringUtils.hasText(token)) {
                return token;
            }
        }

        return null;
    }
}