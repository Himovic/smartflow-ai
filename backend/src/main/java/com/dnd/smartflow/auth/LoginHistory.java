package com.dnd.smartflow.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "login_history")
@Getter
@Setter
public class LoginHistory {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id")
    private UUID tenantId;
    @Column(name = "user_id")
    private UUID userId;
    private String email;
    private boolean success;
    @Column(length = 100)
    private String reason;
    @Column(name = "ip_address", length = 64)
    private String ipAddress;
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();
}
