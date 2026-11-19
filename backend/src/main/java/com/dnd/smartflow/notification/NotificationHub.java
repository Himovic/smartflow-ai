package com.dnd.smartflow.notification;

import com.dnd.smartflow.common.security.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Real-time push channel ({@code /ws/events}) for notifications, alerts and control-tower updates. */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationHub extends TextWebSocketHandler {
    private final ObjectMapper mapper;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    record Session(WebSocketSession ws, UUID userId, UUID tenantId, String role) {
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession ws) throws Exception {
        if (ws.getPrincipal() instanceof Authentication a && a.getPrincipal() instanceof CurrentUser u) {
            sessions.put(ws.getId(), new Session(ws, u.userId(), u.tenantId(), u.role().name()));
        } else {
            ws.close(CloseStatus.POLICY_VIOLATION);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession ws, CloseStatus status) {
        sessions.remove(ws.getId());
    }

    /** Pushes an event to a user, to every holder of a role, or to the whole tenant when both are null. */
    public void push(UUID tenantId, UUID userId, String role, String type, Object payload) {
        String json;
        try {
            json = mapper.writeValueAsString(Map.of("type", type, "payload", payload));
        } catch (Exception e) {
            return;
        }
        for (Session s : sessions.values()) {
            if (!s.tenantId().equals(tenantId)) continue;
            if (userId != null && !userId.equals(s.userId())) continue;
            if (userId == null && role != null && !role.equals(s.role())) continue;
            try {
                synchronized (s.ws()) {
                    if (s.ws().isOpen()) s.ws().sendMessage(new TextMessage(json));
                }
            } catch (Exception e) {
                log.debug("WebSocket push failed: {}", e.getMessage());
            }
        }
    }

    public Set<String> connected() {
        return sessions.keySet();
    }

    @Configuration
    @EnableWebSocket
    @RequiredArgsConstructor
    static class WsConfig implements WebSocketConfigurer {
        private final NotificationHub hub;

        @Override
        public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
            registry.addHandler(hub, "/ws/events").setAllowedOriginPatterns("*");
        }
    }
}
