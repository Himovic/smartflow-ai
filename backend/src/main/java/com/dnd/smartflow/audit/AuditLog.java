package com.dnd.smartflow.audit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Immutable audit trail entry. */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
public class AuditLog {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id")
    private UUID tenantId;
    @Column(name = "user_id")
    private UUID userId;
    @Column(name = "user_email")
    private String userEmail;
    @Column(nullable = false, length = 50)
    private String action;
    @Column(name = "entity_type", length = 80)
    private String entityType;
    @Column(name = "entity_id")
    private UUID entityId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> details;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();
}
