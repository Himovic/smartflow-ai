package com.dnd.smartflow.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Opaque refresh token (stored as SHA-256 hash) representing one user session. */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
public class RefreshToken {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "last_used_at")
    private Instant lastUsedAt;
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    public boolean isActive() {
        return revokedAt == null && expiresAt.isAfter(Instant.now());
    }
}
