package com.dnd.smartflow.notification;

import com.dnd.smartflow.common.security.Role;
import com.dnd.smartflow.common.tenant.TenantContext;
import com.dnd.smartflow.iam.User;
import com.dnd.smartflow.iam.UserRepository;
import com.dnd.smartflow.notification.domain.Notification;
import com.dnd.smartflow.notification.domain.NotificationRepository;
import com.dnd.smartflow.notification.domain.NotificationRule;
import com.dnd.smartflow.notification.domain.NotificationRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Creates in-app notifications, pushes them in real time and, according to the tenant's notification
 * rules, relays them by e-mail. Rules match an event type and list target roles and channels.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notifications;
    private final NotificationRuleRepository rules;
    private final UserRepository users;
    private final NotificationHub hub;
    private final MailGateway mail;

    public record Message(String eventType, String title, String message, String severity, String category,
                          String entityType, UUID entityId) {
    }

    /** Notifies one user directly. */
    @Transactional
    public void notifyUser(UUID userId, Message m) {
        if (userId == null) return;
        Notification n = build(m);
        n.setUserId(userId);
        notifications.save(n);
        hub.push(TenantContext.require(), userId, null, "notification", view(n));
    }

    /** Notifies every role configured for the event (falls back to the given default roles). */
    @Transactional
    public void notifyRoles(Message m, Role... defaultRoles) {
        Set<String> roles = new LinkedHashSet<>();
        Set<String> channels = new LinkedHashSet<>(List.of("IN_APP"));
        List<NotificationRule> matching = rules.findAll().stream()
                .filter(r -> Boolean.TRUE.equals(r.getActive()) && r.getEventType().equals(m.eventType())).toList();
        if (matching.isEmpty()) {
            Arrays.stream(defaultRoles).forEach(r -> roles.add(r.name()));
        } else {
            matching.forEach(r -> {
                if (r.getRoles() != null) roles.addAll(r.getRoles());
                if (r.getChannels() != null) channels.addAll(r.getChannels());
            });
        }
        for (String role : roles) {
            Notification n = build(m);
            n.setTargetRole(role);
            notifications.save(n);
            hub.push(TenantContext.require(), null, role, "notification", view(n));
        }
        if (channels.contains("EMAIL") && mail.isEnabled()) {
            List<Role> rs = roles.stream().map(Role::valueOf).toList();
            for (User u : users.findByRoleInAndActiveTrue(rs)) {
                mail.send(u.getEmail(), "[SmartFlow] " + m.title(), Objects.toString(m.message(), ""));
            }
        }
    }

    private Notification build(Message m) {
        Notification n = new Notification();
        n.setTitle(m.title());
        n.setMessage(m.message());
        n.setSeverity(m.severity() == null ? "INFO" : m.severity());
        n.setCategory(m.category());
        n.setEntityType(m.entityType());
        n.setEntityId(m.entityId());
        return n;
    }

    public static Map<String, Object> view(Notification n) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", n.getId());
        v.put("title", n.getTitle());
        v.put("message", n.getMessage());
        v.put("severity", n.getSeverity());
        v.put("category", n.getCategory());
        v.put("entityType", n.getEntityType());
        v.put("entityId", n.getEntityId());
        v.put("createdAt", n.getCreatedAt());
        v.put("readAt", n.getReadAt());
        return v;
    }
}
